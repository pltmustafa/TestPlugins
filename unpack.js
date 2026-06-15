const fs = require('fs');

function unpack(str) {
    let m = str.match(/eval\(function\(p,a,c,k,e,d\).*?\}\('(.*?)',(\d+),(\d+),'([^']*)'\.split\('\|'\)/);
    if (!m) {
        console.log("No match");
        return;
    }
    let p = m[1];
    let a = parseInt(m[2]);
    let c = parseInt(m[3]);
    let k = m[4].split('|');
    
    let e = function(c) {
        return (c < a ? '' : e(parseInt(c / a))) + ((c % a) > 35 ? String.fromCharCode((c % a) + 29) : (c % a).toString(36));
    };
    
    while (c--) {
        if (k[c]) {
            let regex = new RegExp('\\b' + e(c) + '\\b', 'g');
            p = p.replace(regex, k[c]);
        }
    }
    return p;
}

const text = fs.readFileSync('/tmp/vidlop.html', 'utf8');
const unpacked = unpack(text);
fs.writeFileSync('/tmp/unpacked.js', unpacked);
console.log("Unpacked length:", unpacked.length);
