import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const lesson = resolve(frontend, '..');
const moduleRoot = resolve(lesson, '..');
const temporaryModule = resolve(frontend, '.e2e-work/wrong-order');
const temporaryLesson = resolve(temporaryModule, '21-04-004-request-matching-boundaries');
rmSync(temporaryModule, { recursive: true, force: true });
mkdirSync(temporaryLesson, { recursive: true });
writeFileSync(resolve(temporaryModule, 'pom.xml'), readFileSync(resolve(moduleRoot, 'pom.xml')));
writeFileSync(resolve(temporaryLesson, 'pom.xml'), readFileSync(resolve(lesson, 'pom.xml')));
cpSync(resolve(lesson, 'src/main'), resolve(temporaryLesson, 'src/main'), { recursive: true });
// 故意将宽范围拒绝放到公开特例之前，仅在隔离目录构建。
const configuration = resolve(temporaryLesson, 'src/main/java/cn/ningbingjian/learnjava/security/lesson004/SecurityConfig.java');
// 正式源码始终保持正确顺序。
const correct = readFileSync(configuration, 'utf8');
const publicRule = '.requestMatchers(HttpMethod.GET, "/public/info").permitAll()';
const broadRule = '.requestMatchers("/public/**").denyAll()';
const pair = `${publicRule}\n                ${broadRule}`;
if (!correct.includes(pair)) throw new Error('未找到本课相邻规则，不能构造错序实验。');
const previous = correct.replace(pair, `${broadRule}\n                ${publicRule}`);
writeFileSync(configuration, previous);

function build(cwd, args, name) {
  const result = spawnSync(process.env.MAVEN_CMD || 'mvn', ['-B', '-ntp', ...args], {
    cwd, encoding: 'utf8', timeout: 240000,
  });
  writeFileSync(resolve(frontend, `.e2e-work/${name}.log`), `${result.stdout || ''}\n${result.stderr || ''}`);
  if (result.error || result.status !== 0) {
    throw new Error(`${name}构建失败，请检查 .e2e-work/${name}.log 及 mvn -v 显示的JDK。`);
  }
}

build(lesson, ['clean', 'verify'], 'completed-build');
build(temporaryLesson, ['clean', 'package'], 'before-build');
console.log('已构建规则完成版（含后端测试）与隔离的错误顺序对照版。');
