WEBCONTAINER SERVER SETUP GUIDELINES:

1. HTML/CSS/JS Static Projects:
   ALWAYS create package.json with Vite:
   ```json
   {
     "name": "static-project",
     "scripts": {
       "dev": "vite",
       "build": "vite build",
       "preview": "vite preview"
     },
     "devDependencies": {
       "vite": "^4.2.0"
     }
   }
   ```
   Then use: npm run dev

2. React Projects:
   ```json
   {
     "name": "react-project",
     "scripts": {
       "dev": "vite",
       "build": "vite build"
     },
     "dependencies": {
       "react": "^18.2.0",
       "react-dom": "^18.2.0"
     },
     "devDependencies": {
       "vite": "^4.2.0",
       "@vitejs/plugin-react": "^3.1.0"
     }
   }
   ```

3. Vue Projects:
   ```json
   {
     "name": "vue-project",
     "scripts": {
       "dev": "vite",
       "build": "vite build"
     },
     "dependencies": {
       "vue": "^3.2.0"
     },
     "devDependencies": {
       "vite": "^4.2.0",
       "@vitejs/plugin-vue": "^4.0.0"
     }
   }
   ```

4. Node.js/Express Projects:
   ```json
   {
     "name": "node-project",
     "scripts": {
       "dev": "node server.js",
       "start": "node server.js"
     },
     "dependencies": {
       "express": "^4.18.0"
     }
   }
   ```

CRITICAL RULES:
- NEVER use: npx serve --yes
- NEVER use: npx http-server --yes  
- NEVER use: python -m http.server
- ALWAYS create package.json first
- ALWAYS use npm scripts for server startup
- PREFER Vite for all frontend projects
- USE start action type for npm run dev commands

CORRECT COMMAND PATTERNS:
✅ npm run dev
✅ npm install && npm run dev
✅ node server.js (for Node.js apps)

INCORRECT COMMAND PATTERNS:
❌ npx serve --yes
❌ npx serve .
❌ npx http-server
❌ python3 -m http.server
❌ php -S localhost:8000
