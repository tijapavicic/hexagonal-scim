import { getJson } from './http';
import type { ProductDto } from '../types/product.dto';

const BASE = '/api/v1/products';

/** Fetch the list of sellable products (currently only BaseCatHouse). */
export async function listProducts(): Promise<ProductDto[]> {
  return getJson<ProductDto[]>(BASE);
}

