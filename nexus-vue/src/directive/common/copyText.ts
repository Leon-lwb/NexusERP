function getRealElement(el: any): HTMLElement | null {
  if (!el) return null
  if (typeof el.addEventListener === 'function') return el
  if (el.$el) return getRealElement(el.$el)
  if (el.children && el.children.length > 0) {
    for (let child of el.children) {
      const realEl = getRealElement(child)
      if (realEl) return realEl
    }
  }
  return null
}

export default {
  mounted(el: any, { value, arg }: any) {
    const targetEl = getRealElement(el)
    if (!targetEl || typeof targetEl.addEventListener !== 'function') {
      console.error('copyText directive: Cannot find valid DOM element', el)
      return
    }
    if (arg === "callback") {
      (targetEl as any).$copyCallback = value
    } else {
      (targetEl as any).$copyValue = value
      const handler = () => {
        copyTextToClipboard((targetEl as any).$copyValue)
        if ((targetEl as any).$copyCallback) {
          (targetEl as any).$copyCallback((targetEl as any).$copyValue)
        }
      }
      targetEl.addEventListener("click", handler)
      (targetEl as any).$destroyCopy = () => targetEl.removeEventListener("click", handler)
    }
  },
  beforeUnmount(el: any) {
    const targetEl = getRealElement(el)
    if (targetEl && (targetEl as any).$destroyCopy) {
      (targetEl as any).$destroyCopy()
    }
  }
}

function copyTextToClipboard(input: string, { target = document.body } = {}): boolean {
  const element = document.createElement('textarea')
  const previouslyFocusedElement = document.activeElement

  element.value = input

  element.setAttribute('readonly', '')

  element.style.contain = 'strict'
  element.style.position = 'absolute'
  element.style.left = '-9999px'
  element.style.fontSize = '12pt'

  const selection = document.getSelection()
  const originalRange = selection && selection.rangeCount > 0 && selection.getRangeAt(0)

  target.appendChild(element)
  element.select()

  element.selectionStart = 0
  element.selectionEnd = input.length

  let isSuccess = false
  try {
    isSuccess = document.execCommand('copy')
  } catch (e) {}

  element.remove()

  if (originalRange) {
    selection!.removeAllRanges()
    selection!.addRange(originalRange)
  }

  if (previouslyFocusedElement) {
    (previouslyFocusedElement as HTMLElement).focus()
  }

  return isSuccess
}
