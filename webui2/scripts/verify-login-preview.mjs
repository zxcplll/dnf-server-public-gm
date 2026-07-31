import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/views/Login.vue', import.meta.url), 'utf8');

assert.doesNotMatch(source, /login-bg(?:2)?\.png/);
assert.match(source, /<canvas[^>]+ref="sceneCanvas"/);
assert.match(source, /prefers-reduced-motion/);
assert.match(source, /@submit="login"/);
assert.match(source, /:loading="submitting"/);
assert.match(source, /autocomplete="username"/);
assert.match(source, /autocomplete="current-password"/);
assert.match(source, /100dvh/);
assert.match(source, /@media \(max-width: 860px\)/);

console.log('login preview structure verified');
