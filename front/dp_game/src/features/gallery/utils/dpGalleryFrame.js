/**
 * Classify image aspect ratio (w/h) into gallery frame tier.
 * @param {number} width
 * @param {number} height
 * @returns {'portrait'|'square'|'landscape'}
 */
export function dpGalleryFrameTier (width, height) {
  if (!width || !height || width <= 0 || height <= 0) return 'square'
  var ratio = width / height
  if (ratio < 0.85) return 'portrait'
  if (ratio > 1.15) return 'landscape'
  return 'square'
}

export function dpGalleryFrameClass (width, height) {
  return 'gallery-frame--' + dpGalleryFrameTier(width, height)
}
