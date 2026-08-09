const { existsSync } = require('node:fs');
const { join } = require('node:path');

const edgeWindows = [
  process.env['PROGRAMFILES(X86)'],
  process.env.PROGRAMFILES,
]
  .filter(Boolean)
  .map((directorio) => join(directorio, 'Microsoft', 'Edge', 'Application', 'msedge.exe'))
  .find(existsSync);

// Karma solo reconoce CHROME_BIN para lanzadores basados en Chrome. En Windows
// de desarrollo usamos Edge si Chrome no está instalado; CI puede seguir
// proporcionando su propio CHROME_BIN.
if (!process.env.CHROME_BIN && edgeWindows) {
  process.env.CHROME_BIN = edgeWindows;
}

module.exports = function (config) {
  config.set({
    frameworks: ['jasmine'],
    customLaunchers: {
      EdgeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: [
          '--disable-gpu',
          '--disable-dev-shm-usage',
          '--no-sandbox',
          '--use-angle=swiftshader',
        ],
      },
    },
  });
};
