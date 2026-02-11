export default {
  beforeMount(el: HTMLElement, { value, arg }: any) {
    if (arg === "callback") {
      (el as any).$copyCallback = value
    } else {
      (el as any).$copyValue = value
      const handler = () => {
        copyTextToClipboard((el as any).$copyValue)
        if ((el as any).$copyCallback) {
          (el as any).$copyCallback((el as any).$copyValue)
        }
      }
      el.addEventListener("click", handler)
      (el as any).$destroyCopy = () => el.removeEventListener("click", handler)
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
