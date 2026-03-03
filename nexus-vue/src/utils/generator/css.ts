const styles: Record<string, string> = {
  'el-rate': '.el-rate{display: inline-block; vertical-align: text-top;}',
  'el-upload': '.el-upload__tip{line-height: 1.2;}'
}

function addCss(cssList: string[], el: any): void {
  const css = styles[el.tag]
  css && cssList.indexOf(css) === -1 && cssList.push(css)
  if (el.children) {
    el.children.forEach((el2: any) => addCss(cssList, el2))
  }
}

export function makeUpCss(conf: any): string {
  const cssList: string[] = []
  conf.fields.forEach((el: any) => addCss(cssList, el))
  return cssList.join('\n')
}
