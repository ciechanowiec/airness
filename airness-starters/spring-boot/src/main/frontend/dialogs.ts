// A dialog is opened by a control that names it, because a template carries no code and a native dialog
// opens as a modal only through script. The control is an ordinary button, so without script it does
// nothing, and the action it guards stays one the dialog posts through a form of its own.

const OPENS: string = 'data-opens'

/**
 * Opens the dialog that the clicked control names in its data-opens attribute, as a modal.
 * @param event the click, wherever on the page it landed
 */
export const openNamedDialog = (event: Event): void => {
  const target: EventTarget | null = event.target
  if (!(target instanceof Element)) {
    return
  }
  const name: string = target.closest(`[${CSS.escape(OPENS)}]`)?.getAttribute(OPENS) ?? ''
  if (name === '') {
    return
  }
  const named: Element | null = document.querySelector(`#${CSS.escape(name)}`)
  if (named instanceof HTMLDialogElement) {
    named.showModal()
  }
}
