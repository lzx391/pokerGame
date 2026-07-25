import store from '@/store'

function resolvePerm(binding) {
  var v = binding.value
  if (typeof v === 'string') return v
  if (v && typeof v === 'object' && v.perm) return v.perm
  return ''
}

function applyHasPermi(el, binding) {
  var code = resolvePerm(binding)
  if (!code) {
    el.style.display = 'none'
    return
  }
  var ok = store.getters['dpAuth/hasPerm'](code)
  el.style.display = ok ? '' : 'none'
}

export default {
  inserted: function (el, binding) {
    applyHasPermi(el, binding)
  },
  update: function (el, binding) {
    applyHasPermi(el, binding)
  }
}
