/** One choice of a dropdown or a segmented control. */
export interface UiOption<V extends string | number = string | number> {
  value: V
  label: string
}
