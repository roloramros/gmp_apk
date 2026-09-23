// src/controllers/catalogProductsController.js
//
// CRUD de la tienda de componentes sueltos (feature "sitio profesional").
// Mismo patrón exacto que catalogKitsController.js — ver ese archivo para
// el razonamiento general. Diferencias de dominio: `category` (texto libre)
// en vez de specs de kit, e `in_stock` en vez de specs técnicas.

const pool = require('../db/pool');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const BASE_SELECT = `
  SELECT
    p.uuid, p.category, p.name, p.price_usd, p.description, p.in_stock,
    p.active, p.sort_order, p.created_at, p.updated_at,
    COALESCE(
      (SELECT json_agg(json_build_object(
         'uuid', pp.uuid,
         'url', format('/catalog-products/%s/photos/%s/file', p.uuid, pp.uuid),
         'sort_order', pp.sort_order
       ) ORDER BY pp.sort_order ASC, pp.created_at ASC)
       FROM catalog_product_photos pp
       WHERE pp.product_id = p.id AND pp.deleted_at IS NULL),
      '[]'
    ) AS photos
  FROM catalog_products p
`;

async function createProduct(req, res) {
  const { uuid, category, name, price_usd, description, in_stock, active, sort_order, created_by_device_id } = req.body || {};

  if (!uuid || !UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });
  }
  if (!name || typeof name !== 'string' || !name.trim()) {
    return res.status(400).json({ error_code: 'invalid_name', message: 'name es requerido.' });
  }

  try {
    const result = await pool.query(
      `INSERT INTO catalog_products
         (uuid, company_id, category, name, price_usd, description, in_stock, active, sort_order, created_by_device_id)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
       RETURNING uuid`,
      [
        uuid, req.user.company_id, category || null, name.trim(), price_usd ?? null,
        description || null, in_stock === undefined ? true : !!in_stock,
        active === undefined ? true : !!active, sort_order ?? 0, created_by_device_id || null,
      ]
    );
    return res.status(201).json(await getFullProduct(result.rows[0].uuid));
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe un producto con ese uuid.' });
    }
    console.error('[catalogProducts] Error en createProduct:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al crear el producto.' });
  }
}

async function getFullProduct(uuid) {
  const result = await pool.query(`${BASE_SELECT} WHERE p.uuid = $1`, [uuid]);
  return result.rows[0] || null;
}

async function listProducts(req, res) {
  try {
    const result = await pool.query(
      `${BASE_SELECT}
       WHERE p.company_id = $1 AND p.deleted_at IS NULL
       ORDER BY p.category ASC NULLS LAST, p.sort_order ASC, p.name ASC`,
      [req.user.company_id]
    );
    return res.status(200).json({ products: result.rows });
  } catch (err) {
    console.error('[catalogProducts] Error en listProducts:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al listar los productos.' });
  }
}

async function getProduct(req, res) {
  const { uuid } = req.params;
  if (!UUID_RE.test(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  try {
    const result = await pool.query(
      `${BASE_SELECT} WHERE p.uuid = $1 AND p.company_id = $2 AND p.deleted_at IS NULL`,
      [uuid, req.user.company_id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Producto no encontrado.' });
    return res.status(200).json(result.rows[0]);
  } catch (err) {
    console.error('[catalogProducts] Error en getProduct:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al obtener el producto.' });
  }
}

const EDITABLE_FIELDS = ['category', 'name', 'price_usd', 'description', 'in_stock', 'active', 'sort_order'];

async function updateProduct(req, res) {
  const { uuid } = req.params;
  const body = req.body || {};
  if (!UUID_RE.test(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });

  const fieldsToUpdate = EDITABLE_FIELDS.filter((f) => Object.prototype.hasOwnProperty.call(body, f));
  if (fieldsToUpdate.length === 0) {
    return res.status(400).json({ error_code: 'no_fields', message: `No se enviaron campos editables (${EDITABLE_FIELDS.join(', ')}).` });
  }
  const setClauses = fieldsToUpdate.map((f, i) => `${f} = $${i + 1}`);
  const values = fieldsToUpdate.map((f) => body[f]);

  try {
    const result = await pool.query(
      `UPDATE catalog_products
       SET ${setClauses.join(', ')}, updated_at = now()
       WHERE uuid = $${values.length + 1} AND company_id = $${values.length + 2} AND deleted_at IS NULL
       RETURNING uuid`,
      [...values, uuid, req.user.company_id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Producto no encontrado.' });
    return res.status(200).json(await getFullProduct(result.rows[0].uuid));
  } catch (err) {
    console.error('[catalogProducts] Error en updateProduct:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al actualizar el producto.' });
  }
}

// Soft-delete en cascada del producto + sus fotos (mismo motivo que
// catalogKitsController.deleteKit: que no queden fotos huérfanas en /sync).
async function deleteProduct(req, res) {
  const { uuid } = req.params;
  if (!UUID_RE.test(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const productResult = await client.query(
      `SELECT id FROM catalog_products WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL FOR UPDATE`,
      [uuid, req.user.company_id]
    );
    if (productResult.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error_code: 'not_found', message: 'Producto no encontrado.' });
    }
    const productId = productResult.rows[0].id;
    await client.query(
      `UPDATE catalog_product_photos SET deleted_at = now(), updated_at = now() WHERE product_id = $1 AND deleted_at IS NULL`,
      [productId]
    );
    await client.query(`UPDATE catalog_products SET deleted_at = now(), updated_at = now() WHERE id = $1`, [productId]);
    await client.query('COMMIT');
    return res.status(200).json({ ok: true, uuid });
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('[catalogProducts] Error en deleteProduct:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al eliminar el producto.' });
  } finally {
    client.release();
  }
}

module.exports = { createProduct, listProducts, getProduct, updateProduct, deleteProduct, getFullProduct };
