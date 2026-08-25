const FlowNotNull = (rule, value, callback) => {
  let reg = /^1(3|4|5|7|8)\d{9}$/;
  if (!reg.test(value)) {
    callback('不能为空');
  } else {
    callback();
  }
};

const FlowPhone = (rule, value, callback) => {
  let reg = /^1(3|4|5|7|8)\d{9}$/;
  if (!reg.test(value)) {
    callback('请输入正确手机号');
  } else {
    callback();
  }
};

const FlowTelPhone = (rule, value, callback) => {
  let reg = /^\d{3}-\d{7,8}|\d{4}-\d{7,8}$/;
  if (!reg.test(value)) {
    callback('请输入正确的座机号');
  } else {
    callback();
  }
};

const FlowEmail = (rule, value, callback) => {
  let reg = /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/;
  if (!reg.test(value)) {
    callback('请输入正确的邮箱');
  } else {
    callback();
  }
};

const FlowEmailCode = (rule, value, callback) => {
  let reg = /^[0-9]\d{5}$/;
  if (!reg.test(value)) {
    callback('请输入正确的邮编');
  } else {
    callback();
  }
};

const FlowEn = (rule, value, callback) => {
  let reg = /^[a-zA-Z]+$/;
  if (!reg.test(value)) {
    callback('请输入正确的字母');
  } else {
    callback();
  }
};

const FlowNumber = (rule, value, callback) => {
  let reg = /^[0-9]+$/;
  if (!reg.test(value)) {
    callback('请输入数字');
  } else {
    callback();
  }
};

const FlowZhengShu = (rule, value, callback) => {
  let reg = /^-?[1-9]\d*$/;
  if (!reg.test(value)) {
    callback('请输入整数');
  } else {
    callback();
  }
};

const FlowMoney = (rule, value, callback) => {
  let reg = /^(([1-9][0-9]*)|(([0]\.\d{1,2}|[1-9][0-9]*\.\d{1,2})))$/;
  if (!reg.test(value)) {
    callback('请输入正确金额');
  } else {
    callback();
  }
};

const Flow616Num = (rule, value, callback) => {
  let reg = /^[0-9]+$/;
  let reg2 = /^.{6,16}$/;
  // 长度为6到16位数字
  if (value !== '' && !reg.test(value)) {
    callback('只允许数字');
  } else if (value !== '' && !reg2.test(value)) {
    callback('长度为6到16位数字');
  } else {
    callback();
  }
};

const Flow616All = (rule, value, callback) => {
  let reg = /^.{6,16}$/;
  // 长度为6到16位任意字符
  if (value !== '' && !reg.test(value)) {
    callback('长度为6到16位任意字符');
  } else {
    callback();
  }
};

const Flow618All = (rule, value, callback) => {
  let reg = /^.{6,18}$/;
  // 长度为6到16位任意字符
  if (value !== '' && !reg.test(value)) {
    callback('长度为6到18位任意字符');
  } else {
    callback();
  }
};

const Flow618Val = (rule, value, callback) => {
  let reg = /^[_a-zA-Z0-9]+$/;
  let reg2 = /^.{4,18}$/;
  // 长度为6到18个字符
  if (value !== '' && !reg.test(value)) {
    callback('只允许字母、数字、下划线');
  } else if (value !== '' && !reg2.test(value)) {
    callback('长度6到18个字符');
  } else {
    callback();
  }
};

const FlowNaturNum = () => {
  let reg = /^(([0-9]*[1-9][0-9]*)|(0+))$/;
  if (!reg.test(value)) {
    callback('请输入自然数');
  } else {
    callback();
  }
};

const FlowEFlowh = () => {
  let reg = /^.[A-Za-z]+$/;
  if (!reg.test(value)) {
    callback('请输入英文字符');
  } else {
    callback();
  }
};

const FlowBankNo = () => {
  let reg = /^[1-9]\d{9,19}$/;
  if (!reg.test(value)) {
    callback('请输入正确的银行卡号码');
  } else {
    callback();
  }
};

const FlowIDCard = () => {
  let reg =
    /(^[1-9]\d{5}(18|19|([23]\d))\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\d{3}[0-9Xx]$)|(^[1-9]\d{7}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\d{3}$)/;
  if (!reg.test(value)) {
    callback('请输入正确的身份证号码');
  } else {
    callback();
  }
};

const FlowQQNo = () => {
  let reg = /^[1-9]\d{4,11}$/;
  if (!reg.test(value)) {
    callback('请输入正确的QQ号码');
  } else {
    callback();
  }
};

const FlowWWW = () => {
  let reg = /^(http|https):\/\/[\w\-_]+(\.[\w\-_]+)+([\w\-.,@?^=%&:/~+#]*[\w\-@?^=%&/~+#])?$/;
  if (!reg.test(value)) {
    callback('请输入以http和https开头的网址');
  } else {
    callback();
  }
};

const Flow020 = () => {
  let reg = /^[a-z0-9A-Z]{0,20}$/;
  if (!reg.test(value)) {
    callback('请输入20位以内的英文字符和数字');
  } else {
    callback();
  }
};

// 校验规则集合（供表单 rules 引用）
const FlowRule = {
  FlowNotNull,
  FlowPhone,
  FlowTelPhone,
  FlowEmail,
  FlowEmailCode,
  FlowEn,
  FlowNumber,
  FlowZhengShu,
  FlowMoney,
  Flow616Num,
  Flow616All,
  Flow618All,
  Flow618Val,
  FlowNaturNum,
  FlowEFlowh,
  FlowBankNo,
  FlowIDCard,
  FlowQQNo,
  FlowWWW,
  Flow020
};

export {
  FlowNotNull,
  FlowPhone,
  FlowTelPhone,
  FlowEmail,
  FlowEmailCode,
  FlowEn,
  FlowNumber,
  FlowZhengShu,
  FlowMoney,
  Flow616Num,
  Flow616All,
  Flow618All,
  Flow618Val,
  FlowNaturNum,
  FlowEFlowh,
  FlowBankNo,
  FlowIDCard,
  FlowQQNo,
  FlowWWW,
  Flow020,
  FlowRule
};
