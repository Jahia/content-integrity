#!/usr/bin/env node
/*
 * Builds the PDF, git-ignored, of each Markdown page given as argument, next to it, with its screenshots: by default
 * docs/embedded-tests.pdf and README.pdf. The Markdown is rendered to HTML, then printed to PDF by a headless Chrome.
 * Run it with `yarn docs:pdf`.
 *
 * - The headings get the anchors GitHub gives them, so that the summary and the links between the sections work in the PDF.
 * - The links to the other pages of the repository point to GitHub, where they are published.
 * - The GitHub alerts, such as > [!CAUTION], are rendered as a quote which starts with their title.
 * - Each section, such as a check, starts on a new page.
 *
 * CHROME_PATH sets the Chrome or Chromium executable, found in its default locations otherwise.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const {execFileSync} = require('child_process');
const {pathToFileURL} = require('url');
const {marked} = require('marked');

const ROOT = path.dirname(__dirname);
const DEFAULT_SOURCES = [path.join(__dirname, 'embedded-tests.md'), path.join(ROOT, 'README.md')];
const PUBLISHED_ROOT = 'https://github.com/Jahia/content-integrity/blob/master/';

const CHROME_LOCATIONS = [
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Chromium.app/Contents/MacOS/Chromium',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser'
];

const findChrome = () => {
    const chrome = process.env.CHROME_PATH || CHROME_LOCATIONS.find(location => fs.existsSync(location));
    if (!chrome) {
        throw new Error('Chrome not found: set CHROME_PATH to the Chrome or Chromium executable');
    }

    return chrome;
};

// The anchor GitHub gives a heading: its text in lower case, without the punctuation, the spaces replaced by dashes
const slugOf = text => text.replace(/<[^>]*>/g, '').trim().toLowerCase().replace(/[^\w\- ]/g, '').replace(/ /g, '-');

// The renderer of a page, whose relative links are resolved from its folder on GitHub
const rendererOf = published => {
    const renderer = new marked.Renderer();
    renderer.heading = (text, level, raw) => `<h${level} id="${slugOf(raw)}">${text}</h${level}>\n`;
    renderer.link = (href, title, text) => {
        // The other pages of the repository are not part of the PDF
        const target = href && !/^([a-z]+:|#)/.test(href) ? new URL(href, published).href : href;
        return `<a href="${target}"${title ? ` title="${title}"` : ''}>${text}</a>`;
    };
    return renderer;
};

// A GitHub alert, such as > [!CAUTION], is a quote whose first line is its type
const ALERT = /^> \[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\][ \t]*$/gm;
const withAlertTitles = markdown => markdown.replace(ALERT, (_, type) => `> **${type[0]}${type.slice(1).toLowerCase()}**\n>`);

const STYLE = `
    @page { size: A4; margin: 15mm 12mm; }
    body { font-family: -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; font-size: 10.5pt; line-height: 1.45; color: #1f2328; }
    h1 { font-size: 20pt; border-bottom: 1px solid #d1d9e0; padding-bottom: 4pt; }
    h2 { font-size: 16pt; border-bottom: 1px solid #d1d9e0; padding-bottom: 3pt; page-break-before: always; }
    h3 { font-size: 13pt; }
    h4 { font-size: 11.5pt; }
    h2, h3, h4 { page-break-after: avoid; }
    img { max-width: 100%; border: 1px solid #d1d9e0; page-break-inside: avoid; }
    img[align] { border: none; }
    p:has(> img) { page-break-inside: avoid; }
    code { font-family: SFMono-Regular, Consolas, monospace; font-size: 9pt; background: #f6f8fa; padding: 1pt 3pt; border-radius: 3pt; }
    pre { background: #f6f8fa; padding: 6pt; overflow-wrap: anywhere; white-space: pre-wrap; }
    pre code { padding: 0; }
    table { border-collapse: collapse; width: 100%; font-size: 9pt; page-break-inside: avoid; }
    th, td { border: 1px solid #d1d9e0; padding: 3pt 5pt; text-align: left; vertical-align: top; }
    th { background: #f6f8fa; }
    a { color: #0969da; text-decoration: none; }
    blockquote { margin: 0 0 8pt; padding: 2pt 10pt; border-left: 3pt solid #d1d9e0; color: #59636e; }
`;

// The title of the page: the one of the module, followed by the first heading of the page when it is another one
const titleOf = markdown => {
    const heading = (markdown.match(/^# (.*)$/m) || [])[1];
    const text = heading ? heading.replace(/<[^>]*>/g, '').trim() : '';
    return !text || text === 'Content Integrity' ? 'Content Integrity' : `Content Integrity - ${text}`;
};

const buildPdf = source => {
    const folder = path.dirname(source);
    const pdf = path.join(folder, `${path.basename(source, '.md')}.pdf`);
    const relativeFolder = path.relative(ROOT, folder);
    const published = PUBLISHED_ROOT + (relativeFolder ? `${relativeFolder.split(path.sep).join('/')}/` : '');
    const markdown = fs.readFileSync(source, 'utf8');
    const body = marked.parse(withAlertTitles(markdown), {renderer: rendererOf(published), gfm: true});
    // The HTML is written to a temporary folder: its images are resolved from the folder of the page
    const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<base href="${pathToFileURL(folder + path.sep).href}">
<title>${titleOf(markdown)}</title>
<style>${STYLE}</style>
</head>
<body>
${body}
</body>
</html>
`;

    const work = fs.mkdtempSync(path.join(os.tmpdir(), 'content-integrity-pdf-'));
    try {
        const htmlFile = path.join(work, `${path.basename(source, '.md')}.html`);
        fs.writeFileSync(htmlFile, html);
        execFileSync(findChrome(), [
            '--headless=new',
            '--disable-gpu',
            '--no-pdf-header-footer',
            '--allow-file-access-from-files',
            `--user-data-dir=${path.join(work, 'chrome')}`,
            `--print-to-pdf=${pdf}`,
            pathToFileURL(htmlFile).href
        ], {stdio: ['ignore', 'ignore', 'pipe']});
    } finally {
        fs.rmSync(work, {recursive: true, force: true});
    }

    console.log(`Written ${path.relative(process.cwd(), pdf)}`);
};

const sources = process.argv.length > 2 ? process.argv.slice(2).map(source => path.resolve(source)) : DEFAULT_SOURCES;
sources.forEach(buildPdf);
