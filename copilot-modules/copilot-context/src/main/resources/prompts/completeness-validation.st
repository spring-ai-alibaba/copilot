COMPLETENESS VALIDATION CHECKLIST:

Before submitting your response, verify EVERY file meets these criteria:

HTML FILES MUST HAVE:
✅ <!DOCTYPE html> declaration
✅ <html lang="..."> with language attribute
✅ Complete <head> section with:
   - <meta charset="UTF-8">
   - <meta name="viewport" content="width=device-width, initial-scale=1.0">
   - <title>...</title>
   - <link rel="stylesheet" href="style.css"> (if CSS file exists)
✅ Complete <body> section with:
   - All content properly structured
   - All opening tags have closing tags
   - <script src="script.js"></script> at end (if JS file exists)
✅ NO placeholder comments like "<!-- content -->"
✅ NO missing closing tags like </div>, </section>, </a>

CSS FILES MUST HAVE:
✅ Complete selectors for ALL HTML elements
✅ NO placeholder comments like "/* styles here */"
✅ Proper CSS syntax with semicolons
✅ Complete responsive design rules
✅ All properties properly defined

JAVASCRIPT FILES MUST HAVE:
✅ Complete function implementations
✅ NO placeholder comments like "// code here"
✅ Proper event listeners
✅ Complete DOM manipulation code
✅ Proper syntax with semicolons

PACKAGE.JSON MUST HAVE:
✅ Correct project name
✅ Complete scripts section
✅ All necessary dependencies listed
✅ Proper JSON syntax

COMMAND SEQUENCE MUST BE:
✅ package.json created first
✅ All project files created completely
✅ npm install command included
✅ npm run dev command last

VALIDATION QUESTIONS TO ASK YOURSELF:
1. Can I copy-paste this HTML and open it in a browser without errors?
2. Are ALL HTML tags properly closed?
3. Will the CSS actually style the HTML elements?
4. Will the JavaScript run without syntax errors?
5. Are there any placeholder comments or incomplete sections?
6. Will npm install work with this package.json?
7. Will npm run dev start the server successfully?

IF ANY ANSWER IS "NO" OR "MAYBE", THE CODE IS INCOMPLETE!

REMEMBER: 
- 32K token limit allows for COMPLETE code generation
- NEVER sacrifice completeness for brevity
- EVERY file must be production-ready
- NO shortcuts or placeholders allowed
