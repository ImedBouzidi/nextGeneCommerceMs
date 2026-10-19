/**
 * Builds the public URL for a stored product image.
 *
 * The catalogue stores bare filenames (`8f2c...-uuid.jpg`) in `Product.images`, not
 * URLs. They are written to `<app.upload.dir>/products/` and served back by
 * catalogue-service's WebConfig at `/uploads/products/**`, reachable through the
 * API gateway at `/api/uploads/products/**`.
 *
 * Kept in one place because the value on its own is not a usable URL — each caller
 * used to build the prefix slightly differently, which is how the admin product
 * list ended up rendering raw filenames as <img src>.
 */
export function productImageBaseUrl(config: any): string {
  const apiFromConfig: string = (config?.apiUrl || '').replace(/\/$/, '');
  if (apiFromConfig.includes('api-gateway')) {
    // When running locally (not docker), the hostname 'api-gateway' may not resolve
    // from the browser. Use the local host and API gateway port instead.
    return `${globalThis.location.protocol}//${globalThis.location.hostname}:8081/api/uploads/products`;
  }
  if (apiFromConfig.endsWith('/api')) {
    return `${apiFromConfig}/uploads/products`;
  }
  if (/^https?:\/\/[^/]+:\d+$/.test(apiFromConfig)) {
    // host:port without /api
    return `${apiFromConfig}/api/uploads/products`;
  }
  return `${apiFromConfig}/uploads/products`;
}

/** Placeholder shown when a product has no image, or its image 404s. */
export const PRODUCT_PLACEHOLDER = '/assets/img/product-placeholder.svg';

/** Placeholder shown when a user has no profile picture. */
export const AVATAR_PLACEHOLDER = '/assets/img/default-avatar.svg';

/**
 * Swaps a broken <img> for the placeholder exactly once.
 *
 * Every component used to guard this by checking `!src.includes(...)` against a
 * *different* filename than the one it assigned, so when the fallback itself 404'd
 * the error handler re-fired indefinitely. Guarding on the placeholder we assign
 * makes the swap idempotent, whatever happens to the asset.
 */
export function useImageFallback(img: HTMLImageElement | null, fallback: string = PRODUCT_PLACEHOLDER): void {
  if (!img || !img.src) return;
  // Already showing a placeholder — either ours, or one we just installed.
  if (img.src.includes(fallback) || img.dataset['fallbackApplied'] === 'true') return;
  img.dataset['fallbackApplied'] = 'true';
  img.src = fallback;
}

/** Resolves a single stored image filename to a loadable URL. */
export function productImageUrl(baseUrl: string, filename: string | null | undefined): string {
  if (!filename) return '';
  // Already absolute or server-relative (legacy data URLs, full URLs, root paths)
  // — leave untouched.
  if (/^(https?:|data:|blob:)/.test(filename) || filename.startsWith('/')) return filename;
  return `${baseUrl}/${filename}`;
}
