
import { getJson, postJson, putJson, deleteVoid } from './http';
import type { ProductDto } from '../types/product.dto';

const BASE = '/api/v1/products';

/** Fetch the list of sellable products. */
export async function listProducts(): Promise<ProductDto[]> {
  return getJson<ProductDto[]>(BASE);
}

/** Fetch a single product by ID. */
export async function getProductById(id: number): Promise<ProductDto> {
  return getJson<ProductDto>(`${BASE}/${id}`);
}

/** Create a new product. Returns the created product (201 Created). */
export async function createProduct(body: Partial<ProductDto>): Promise<ProductDto> {
  return postJson<ProductDto>(BASE, body);
}

/** Update a product by ID. Returns the updated product. */
export async function updateProduct(id: number, body: Partial<ProductDto>): Promise<ProductDto> {
  return putJson<ProductDto>(`${BASE}/${id}`, body);
}

/** Delete a product by ID. Returns void (204 No Content). */
export async function deleteProduct(id: number): Promise<void> {
  return deleteVoid(`${BASE}/${id}`);
}
