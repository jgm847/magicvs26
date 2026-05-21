const https = require('https');
const fs = require('fs');

const url = 'https://registry.npmjs.org/@esbuild/linux-x64/-/linux-x64-0.27.3.tgz';
const file = fs.createWriteStream('esbuild-linux.tgz');

console.log('Downloading esbuild for Linux...');

https.get(url, { rejectUnauthorized: false }, (response) => {
  if (response.statusCode !== 200) {
    console.error(`Failed to download: ${response.statusCode}`);
    process.exit(1);
  }
  
  response.pipe(file);
  
  file.on('finish', () => {
    file.close();
    console.log('Download complete.');
  });
}).on('error', (err) => {
  fs.unlink('esbuild-linux.tgz', () => {});
  console.error(`Error: ${err.message}`);
});
