const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const globalModules = execFileSync('npm.cmd', ['root', '-g'], { shell: true, encoding: 'utf8' }).trim();
const ts = require(path.join(globalModules, 'typescript'));
const source = fs.readFileSync(path.join(__dirname, '../supabase/functions/delete-account/index.ts'), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;

async function test(name, config, expected) {
    let handler;
    const calls = [];
    const fetchMock = async (url, options = {}) => {
        calls.push({ url, options });
        if (url.endsWith('/auth/v1/user')) {
            return Response.json({ id: 'user-test', email: 'test@example.invalid',
                app_metadata: { providers: [config.email ? 'email' : 'google'] } }, { status: config.invalidToken ? 401 : 200 });
        }
        if (url.includes('grant_type=password')) return Response.json({ user: { id: 'user-test' } }, { status: config.badPassword ? 400 : 200 });
        if (url.includes('/rest/v1/workshops')) return Response.json([{ id: 'workshop-test' }]);
        if (url.includes('/storage/v1/object/list/')) {
            const { prefix, offset } = JSON.parse(options.body);
            return Response.json(offset > 0 ? [] : prefix === 'workshop-test'
                ? [{ id: null, name: 'folder' }] : [{ id: 'file-test', name: 'photo.png' }]);
        }
        if (url.includes('/storage/v1/object/')) {
            const prefixes = JSON.parse(options.body).prefixes;
            assert(prefixes.every(prefix => prefix.startsWith('workshop-test/')));
            return Response.json({}, { status: config.storageFailure ? 500 : 200 });
        }
        if (url.endsWith('/auth/v1/admin/users/user-test')) return Response.json({});
        throw new Error('Unexpected URL: ' + url);
    };
    vm.runInNewContext(code, { Request, Response, fetch: fetchMock, Deno: {
        env: { get: key => key === 'SUPABASE_URL' ? 'https://supabase.invalid' : 'synthetic-key' },
        serve: fn => { handler = fn; },
    } });
    const headers = { 'Content-Type': 'application/json' };
    if (!config.noToken) headers.Authorization = 'Bearer synthetic-token';
    const reply = await handler(new Request('https://edge.invalid/delete-account', {
        method: config.method || 'POST', headers,
        ...(config.method === 'OPTIONS' ? {} : { body: JSON.stringify({
            confirmation: config.unconfirmed ? '' : 'ELIMINAR', password: config.password || '',
        }) }),
    }));
    assert.equal(reply.status, expected, name);
    if (expected !== 200 || config.method === 'OPTIONS') {
        assert(!calls.some(call => call.url.includes('/admin/users/')), 'No debe borrar sin validacion completa');
    }
    console.log('PASS: ' + name);
}

(async () => {
    await test('preflight', { method: 'OPTIONS' }, 200);
    await test('sin sesion', { noToken: true }, 401);
    await test('confirmacion obligatoria', { unconfirmed: true }, 400);
    await test('token invalido', { invalidToken: true }, 401);
    await test('correo exige contrasena', { email: true }, 400);
    await test('contrasena incorrecta', { email: true, password: 'synthetic', badPassword: true }, 403);
    await test('fallo storage conserva cuenta', { storageFailure: true }, 502);
    await test('Google elimina solo archivos propios', {}, 200);
    await test('correo validado', { email: true, password: 'synthetic' }, 200);
})().catch(error => { console.error(error.message); process.exitCode = 1; });
