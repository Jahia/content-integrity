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
// The quote of an alert is then styled after its type, as GitHub displays it
const ALERT_QUOTE = /<blockquote>\s*<p><strong>(Note|Tip|Important|Warning|Caution)<\/strong><\/p>/g;
const withAlertClasses = html => html.replace(ALERT_QUOTE, (_, type) => `<blockquote class="alert alert-${type.toLowerCase()}">\n<p class="alert-title">${type}</p>`);
// A cell of a single word, such as an option (--excludeFixedErrors), a type or a default value, is not wrapped
const WORD_CELL = /<td([^>]*)>([^<\s]{1,40}|<code>[^<\s]{1,40}<\/code>)<\/td>/g;
const withWordCells = html => html.replace(WORD_CELL, '<td$1 class="word">$2</td>');

// The title of the document is repeated at the top of each page but the first one, with the page number at the bottom
const pageStyleOf = title => `
    @page { size: A4; margin: 18mm 14mm 16mm;
        @top-right { content: "${title.replace(/["\\]/g, '\\$&')}"; font: 8pt -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; color: #59636e; }
        @bottom-center { content: counter(page) " / " counter(pages); font: 8pt -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; color: #59636e; }
    }
    @page :first { @top-right { content: none; } }
`;

const STYLE = `
    body { font-family: -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; font-size: 10.5pt; line-height: 1.5; color: #1f2328; }
    h1 { font-size: 22pt; border-bottom: 2px solid #00a0e3; padding-bottom: 6pt; margin-top: 0; }
    h2 { font-size: 16pt; color: #0b1c35; border-bottom: 1px solid #d1d9e0; padding-bottom: 3pt; margin-top: 0; page-break-before: always; }
    h3 { font-size: 13pt; color: #0b1c35; margin-top: 18pt; }
    h4 { font-size: 11.5pt; color: #0b1c35; margin-top: 14pt; }
    h2, h3, h4 { page-break-after: avoid; }
    /* A screenshot fits under some text, rather than leaving the end of the page empty */
    img { max-width: 100%; max-height: 90mm; width: auto; border: 1px solid #d1d9e0; border-radius: 4pt; page-break-inside: avoid; }
    img[align] { border: none; border-radius: 0; }
    p:has(> img) { page-break-inside: avoid; text-align: center; }
    code { font-family: SFMono-Regular, Menlo, Consolas, monospace; font-size: 8.5pt; background: #eff2f5; padding: 1pt 3pt; border-radius: 3pt; }
    pre { font-size: 8pt; line-height: 1.4; background: #f6f8fa; border: 1px solid #e1e6eb; border-radius: 4pt; padding: 7pt 9pt; white-space: pre-wrap; page-break-inside: avoid; }
    pre code { font-size: inherit; background: none; padding: 0; }
    table { border-collapse: collapse; width: 100%; font-size: 9pt; margin: 6pt 0 10pt; page-break-inside: avoid; }
    th, td { border: 1px solid #d1d9e0; padding: 4pt 6pt; text-align: left; vertical-align: top; }
    th { background: #f0f3f6; font-weight: 600; }
    tr:nth-child(even) td { background: #fafbfc; }
    td.word { white-space: nowrap; }
    a { color: #0969da; text-decoration: none; }
    ul { padding-left: 18pt; }
    li { margin: 2pt 0; }
    blockquote { margin: 0 0 10pt; padding: 4pt 12pt; border-left: 3pt solid #d1d9e0; color: #59636e; }
    blockquote.alert { color: #1f2328; border-radius: 0 4pt 4pt 0; page-break-inside: avoid; }
    blockquote.alert p { margin: 4pt 0; }
    .alert-title { font-weight: 600; }
    .alert-note { border-left-color: #0969da; background: #f0f6ff; } .alert-note .alert-title { color: #0969da; }
    .alert-tip { border-left-color: #1a7f37; background: #effaf2; } .alert-tip .alert-title { color: #1a7f37; }
    .alert-important { border-left-color: #8250df; background: #f6f2ff; } .alert-important .alert-title { color: #8250df; }
    .alert-warning { border-left-color: #9a6700; background: #fff8e5; } .alert-warning .alert-title { color: #9a6700; }
    .alert-caution { border-left-color: #cf222e; background: #fff1f0; } .alert-caution .alert-title { color: #cf222e; }
`;

// The title of the page: its first heading, prefixed with the name of the module when it does not start with it
const titleOf = markdown => {
    const heading = (markdown.match(/^# (.*)$/m) || [])[1];
    const text = heading ? heading.replace(/<[^>]*>/g, '').trim() : '';
    if (!text) return 'Content Integrity';
    return text.startsWith('Content Integrity') ? text : `Content Integrity - ${text}`;
};

const buildPdf = source => {
    const folder = path.dirname(source);
    const pdf = path.join(folder, `${path.basename(source, '.md')}.pdf`);
    const relativeFolder = path.relative(ROOT, folder);
    const published = PUBLISHED_ROOT + (relativeFolder ? `${relativeFolder.split(path.sep).join('/')}/` : '');
    const markdown = fs.readFileSync(source, 'utf8');
    const body = withWordCells(withAlertClasses(marked.parse(withAlertTitles(markdown), {renderer: rendererOf(published), gfm: true})));
    const title = titleOf(markdown);
    // The HTML is written to a temporary folder: its images are resolved from the folder of the page
    const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<base href="${pathToFileURL(folder + path.sep).href}">
<title>${title}</title>
<style>${pageStyleOf(title)}${STYLE}</style>
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
