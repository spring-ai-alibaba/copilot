/**
 * 自定义JSON解析器，用于处理大整数精度丢失问题
 * 通过预处理JSON字符串来保持大整数的精度
 */
export function safeJsonParse(jsonString: string): any {
  // 预处理JSON字符串，将大整数替换为字符串字面量
  // 匹配JSON中的数值：冒号后跟16位以上的数字，但排除已经在字符串中的数字
  const processedJsonString = jsonString.replace(/":\s*(\d{16,})/g, '": "$1"');

  console.log('[safeJsonParse] 原始JSON:', jsonString);
  console.log('[safeJsonParse] 处理后JSON:', processedJsonString);

  return JSON.parse(processedJsonString, (key, value) => {
    // 对于已经被转换为字符串的大整数，保持为字符串
    if (typeof value === 'string' &&
        /^\d{16,}$/.test(value) &&
        (key === 'userId' ||
         key === 'id' ||
         key === 'githubId' ||
         key === 'wechatId' ||
         key.includes('Id') ||
         key.includes('ID'))) {
      console.log(`[safeJsonParse] 保持大整数字段 ${key}: "${value}"`)
      return value
    }
    return value
  })
}

/**
 * 安全的JSON字符串化，处理大整数
 */
export function safeJsonStringify(obj: any): string {
  return JSON.stringify(obj, (key, value) => {
    // 如果是大整数字符串，确保在序列化时保持为字符串
    if (typeof value === 'string' &&
        /^\d+$/.test(value) &&
        value.length > 15) {
      return value
    }
    return value
  })
}

// 测试函数：验证大整数处理是否正确
export function testBigIntHandling() {
  // 模拟真实的API响应JSON字符串（后端返回的原始格式）
  const testAuthResponseJson = '{"code":200,"msg":"操作成功","data":{"userId":2011367888312393730,"token":null,"userType":"sys_user","loginTime":null,"expireTime":null,"ipaddr":null,"loginLocation":null,"browser":null,"os":null,"username":"admin","nickName":null,"avatar":null,"roleId":null,"loginId":"sys_user:2011367888312393730"}}';

  console.log('=== 测试真实API响应 ===')
  console.log('原始API响应JSON:', testAuthResponseJson)

  const parsedResponse = safeJsonParse(testAuthResponseJson)
  console.log('解析后响应数据:', parsedResponse)

  const userId = parsedResponse?.data?.userId
  console.log('userId:', userId, typeof userId)

  const expectedValue = "2011367888312393730"
  const isCorrect = userId === expectedValue

  console.log('期望值:', expectedValue)
  console.log('实际值:', userId)
  console.log('是否正确:', isCorrect)

  return isCorrect
}
