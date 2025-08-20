// 添加解析上下文的函数
export const parseFileFromContext = (filePath: string, content: string) => {
    const regex = new RegExp(
      `<boltAction[^>]*filePath="${filePath}"[^>]*>([\\s\\S]*?)<\\/boltAction>`
    );
    const match = content.match(regex);
    if (match) {
      return match[1].trim();
    }
    return null;
  };
  