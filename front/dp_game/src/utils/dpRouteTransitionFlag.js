/**
 * 路由转场 feature flag：localStorage.dp_route_transition
 * '' / '1' = 开（默认），'0' = 关
 */
var STORAGE_KEY = 'dp_route_transition'

export function isRouteTransitionEnabled() {
  try {
    var v = localStorage.getItem(STORAGE_KEY)
    return v !== '0'
  } catch (e) {
    return true
  }
}

export function syncDpBodyRouteTransitionFlag() {
  try {
    if (isRouteTransitionEnabled()) {
      document.body.removeAttribute('data-dp-route-transition')
    } else {
      document.body.setAttribute('data-dp-route-transition', 'off')
    }
  } catch (e) {
    /* ignore */
  }
}
