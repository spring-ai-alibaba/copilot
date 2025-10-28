interface ParsedMessage {
  content: string;
  files?: Record<string, string>;
}

// 预编译正则表达式 - 支持多种格式和语法错误
// 1. <boltAction type="file" filePath="...">
// 2. <boltAction filePath="...">
// 3. <boltAction type="file filePath="..."> (缺少引号)
const BOLT_ACTION_REGEX =
  /<boltAction(?:\s+type="?file"?)?\s+filePath="([^"]+)">([\s\S]*?)<\/boltAction>/g;

// 用于提取 boltArtifact 内容的正则表达式
const BOLT_ARTIFACT_REGEX =
  /<boltArtifact[^>]*>([\s\S]*?)<\/boltArtifact>/g;

export function parseMessage(content: string): ParsedMessage {
  try {
    if (!content || typeof content !== 'string') {
      return { content: content || '' };
    }

    const files: Record<string, string> = {};
    let searchContent = content;

    // 首先尝试提取 boltArtifact 内容
    const artifactMatches = Array.from(content.matchAll(BOLT_ARTIFACT_REGEX));
    if (artifactMatches.length > 0) {
      // 合并所有 artifact 内容
      searchContent = artifactMatches.map(match => match[1]).join('\n');
    }

    // 在提取的内容中查找 boltAction
    let boltMatch;
    BOLT_ACTION_REGEX.lastIndex = 0;

    while ((boltMatch = BOLT_ACTION_REGEX.exec(searchContent)) !== null) {
      const [fullMatch, filePath, fileContent] = boltMatch;

      if (fileContent) {
        files[filePath] = fileContent.trim();
      }
    }

    const fileKeys = Object.keys(files);

    const result = {
      content: `${JSON.stringify(fileKeys)}`,
      files,
    };

    return result;
  } catch (error) {
    return { content: content || '' };
  }
}


