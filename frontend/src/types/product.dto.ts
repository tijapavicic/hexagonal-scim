/**
 * Product as returned by GET /api/v1/products.
 * Only products purchasable in the current phase are returned (BaseCatHouse).
 */
export interface ProductDto {
  id: number;
  name: string;
  description: string;
  /** Base price in the product's native currency. */
  price: string;
  currency: string;
  stockQuantity: number;
}

