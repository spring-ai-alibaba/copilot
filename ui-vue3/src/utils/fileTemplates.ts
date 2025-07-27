// 文件模板工具
export const getFileTemplate = (filePath: string): string => {
  if (filePath.endsWith('.html')) {
    return [
      '<!DOCTYPE html>',
      '<html lang="en">',
      '<head>',
      '    <meta charset="UTF-8">',
      '    <meta name="viewport" content="width=device-width, initial-scale=1.0">',
      '    <title>Document</title>',
      '    <style>',
      '        body {',
      '            font-family: Arial, sans-serif;',
      '            margin: 40px;',
      '            background-color: #f5f5f5;',
      '        }',
      '        .container {',
      '            max-width: 800px;',
      '            margin: 0 auto;',
      '            background: white;',
      '            padding: 20px;',
      '            border-radius: 8px;',
      '            box-shadow: 0 2px 10px rgba(0,0,0,0.1);',
      '        }',
      '        h1 {',
      '            color: #333;',
      '            text-align: center;',
      '        }',
      '        p {',
      '            line-height: 1.6;',
      '            color: #666;',
      '        }',
      '    </style>',
      '</head>',
      '<body>',
      '    <div class="container">',
      '        <h1>Hello World!</h1>',
      '        <p>这是一个示例 HTML 文件。您可以在这里编辑内容。</p>',
      '        <p>当前时间: <span id="time"></span></p>',
      '    </div>',
      '    ',
      '    <script>',
      '        document.getElementById("time").textContent = new Date().toLocaleString();',
      '    </script>',
      '</body>',
      '</html>'
    ].join('\n')
  }
  
  if (filePath.endsWith('.js')) {
    return [
      '// JavaScript 文件',
      'console.log("Hello from ' + filePath + '");',
      '',
      '// 示例函数',
      'function greet(name) {',
      '    return "Hello, " + name + "!";',
      '}',
      '',
      '// 调用函数',
      'console.log(greet("World"));',
      '',
      '// 当前时间',
      'console.log("当前时间:", new Date().toLocaleString());'
    ].join('\n')
  }
  
  if (filePath.endsWith('.css')) {
    return [
      '/* CSS 样式文件 */',
      'body {',
      '    font-family: Arial, sans-serif;',
      '    margin: 0;',
      '    padding: 20px;',
      '    background-color: #f5f5f5;',
      '}',
      '',
      '.container {',
      '    max-width: 800px;',
      '    margin: 0 auto;',
      '    background: white;',
      '    padding: 20px;',
      '    border-radius: 8px;',
      '    box-shadow: 0 2px 10px rgba(0,0,0,0.1);',
      '}'
    ].join('\n')
  }
  
  if (filePath.endsWith('.json')) {
    return JSON.stringify({
      "name": "sample-project",
      "version": "1.0.0",
      "description": "A sample project",
      "main": "index.js",
      "scripts": {
        "start": "node index.js",
        "dev": "node index.js"
      },
      "keywords": ["sample", "demo"],
      "author": "Your Name",
      "license": "MIT"
    }, null, 2)
  }
  
  return ''
}
