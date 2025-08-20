// 测试数据和函数
export const testGameData = `<boltArtifact id="snake-game" title="贪吃蛇游戏">
  <boltAction type="file" filePath="index.html">
<!DOCTYPE html>
<html lang="zh">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>贪吃蛇游戏</title>
    <style>
        body {
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            margin: 0;
            background-color: #333;
            font-family: Arial, sans-serif;
        }
        canvas {
            border: 2px solid #fff;
            background-color: #000;
        }
        .score {
            color: white;
            font-size: 24px;
            margin-bottom: 10px;
            text-align: center;
        }
    </style>
</head>
<body>
    <div>
        <div class="score">得分: <span id="score">0</span></div>
        <canvas id="gameCanvas" width="400" height="400"></canvas>
    </div>
    <script src="game.js"></script>
</body>
</html>
  </boltAction>
  <boltAction type="file" filePath="game.js">
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
const scoreElement = document.getElementById('score');

const gridSize = 20;
const tileCount = canvas.width / gridSize;

let snake = [
    {x: 10, y: 10}
];
let food = {};
let dx = 0;
let dy = 0;
let score = 0;

function randomFood() {
    food = {
        x: Math.floor(Math.random() * tileCount),
        y: Math.floor(Math.random() * tileCount)
    };
}

function drawGame() {
    // 清空画布
    ctx.fillStyle = 'black';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // 绘制蛇
    ctx.fillStyle = 'lime';
    for (let segment of snake) {
        ctx.fillRect(segment.x * gridSize, segment.y * gridSize, gridSize - 2, gridSize - 2);
    }
    
    // 绘制食物
    ctx.fillStyle = 'red';
    ctx.fillRect(food.x * gridSize, food.y * gridSize, gridSize - 2, gridSize - 2);
}

function moveSnake() {
    const head = {x: snake[0].x + dx, y: snake[0].y + dy};
    
    // 检查碰撞
    if (head.x < 0 || head.x >= tileCount || head.y < 0 || head.y >= tileCount) {
        resetGame();
        return;
    }
    
    // 检查是否撞到自己
    for (let segment of snake) {
        if (head.x === segment.x && head.y === segment.y) {
            resetGame();
            return;
        }
    }
    
    snake.unshift(head);
    
    // 检查是否吃到食物
    if (head.x === food.x && head.y === food.y) {
        score += 10;
        scoreElement.textContent = score;
        randomFood();
    } else {
        snake.pop();
    }
}

function resetGame() {
    snake = [{x: 10, y: 10}];
    dx = 0;
    dy = 0;
    score = 0;
    scoreElement.textContent = score;
    randomFood();
}

function gameLoop() {
    moveSnake();
    drawGame();
}

document.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowUp' && dy !== 1) {
        dx = 0;
        dy = -1;
    } else if (e.key === 'ArrowDown' && dy !== -1) {
        dx = 0;
        dy = 1;
    } else if (e.key === 'ArrowLeft' && dx !== 1) {
        dx = -1;
        dy = 0;
    } else if (e.key === 'ArrowRight' && dx !== -1) {
        dx = 1;
        dy = 0;
    }
});

// 初始化游戏
randomFood();
setInterval(gameLoop, 100);
  </boltAction>
  <boltAction type="file" filePath="package.json">
{
  "name": "snake-game",
  "version": "1.0.0",
  "description": "A simple snake game",
  "main": "index.html",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "devDependencies": {
    "vite": "^4.0.0"
  }
}
  </boltAction>
  <boltAction type="file" filePath="README.md">
# 贪吃蛇游戏

一个简单的贪吃蛇游戏，使用HTML5 Canvas实现。

## 如何游戏

- 使用方向键控制蛇的移动
- 吃到红色食物可以增长并得分
- 避免撞墙或撞到自己

## 运行方法

1. 安装依赖：\`npm install\`
2. 启动开发服务器：\`npm run dev\`
3. 在浏览器中打开显示的地址

祝你游戏愉快！
  </boltAction>
</boltArtifact>`;

export const testSimpleData = `<boltArtifact id="simple-test" title="简单测试">
  <boltAction filePath="test.html">
<!DOCTYPE html>
<html>
<head>
    <title>测试页面</title>
</head>
<body>
    <h1>Hello World</h1>
    <p>这是一个测试页面</p>
</body>
</html>
  </boltAction>
</boltArtifact>`;
