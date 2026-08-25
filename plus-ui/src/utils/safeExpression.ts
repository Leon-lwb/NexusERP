/**
 * 安全的四则运算表达式求值。
 *
 * 用于表单设计器配置的计算公式（onSubChange.formula / summary.formula 等），
 * 公式内容来自用户可编辑的表单定义 JSON，严禁直接 eval —— 持有表单设计权限的人
 * 可借此执行任意 JS。本实现仅支持 + - * / % ( ) 与数字（含一元正负号），
 * 出现任何其它语法一律返回 fallback。
 */
export function safeArithmeticEval(expression: string, fallback: number = 0): number {
  if (!expression || typeof expression !== 'string') {
    return fallback;
  }
  const tokens = tokenize(expression);
  if (!tokens || tokens.length === 0) {
    return fallback;
  }
  let pos = 0;
  const peek = () => tokens[pos];

  // expr := term (('+' | '-') term)*
  const parseExpr = (): number | null => {
    let left = parseTerm();
    if (left === null) return null;
    while (peek() === '+' || peek() === '-') {
      const op = tokens[pos++];
      const right = parseTerm();
      if (right === null) return null;
      left = op === '+' ? left + right : left - right;
    }
    return left;
  };

  // term := factor (('*' | '/' | '%') factor)*
  const parseTerm = (): number | null => {
    let left = parseFactor();
    if (left === null) return null;
    while (peek() === '*' || peek() === '/' || peek() === '%') {
      const op = tokens[pos++];
      const right = parseFactor();
      if (right === null) return null;
      left = op === '*' ? left * right : op === '/' ? left / right : left % right;
    }
    return left;
  };

  // factor := ('+' | '-') factor | '(' expr ')' | number
  const parseFactor = (): number | null => {
    const token = peek();
    if (token === '+') {
      pos++;
      return parseFactor();
    }
    if (token === '-') {
      pos++;
      const value = parseFactor();
      return value === null ? null : -value;
    }
    if (token === '(') {
      pos++;
      const value = parseExpr();
      if (value === null || peek() !== ')') return null;
      pos++;
      return value;
    }
    if (token !== undefined && /^[0-9]+(\.[0-9]+)?$/.test(token)) {
      pos++;
      return parseFloat(token);
    }
    return null;
  };

  const result = parseExpr();
  // 必须消耗完全部 token 且结果为有限数，否则视为非法公式
  if (result === null || pos !== tokens.length || !isFinite(result)) {
    return fallback;
  }
  return result;
}

/**
 * 词法分析：仅接受数字、+ - * / % ( ) 与空白，其它字符（字母、引号、分号等）返回 null
 */
function tokenize(expression: string): string[] | null {
  const tokens: string[] = [];
  let i = 0;
  while (i < expression.length) {
    const ch = expression[i];
    if (/\s/.test(ch)) {
      i++;
      continue;
    }
    if (/[0-9.]/.test(ch)) {
      let num = '';
      while (i < expression.length && /[0-9.]/.test(expression[i])) {
        num += expression[i++];
      }
      // ".5" / "1.2.3" 之类非法数字
      if (!/^[0-9]+(\.[0-9]+)?$/.test(num)) {
        return null;
      }
      tokens.push(num);
      continue;
    }
    if ('+-*/%()'.includes(ch)) {
      tokens.push(ch);
      i++;
      continue;
    }
    return null;
  }
  return tokens;
}
