import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const lesson = resolve(frontend, '..');
const moduleRoot = resolve(lesson, '..');
const temporaryModule = resolve(frontend, '.e2e-work/before-users');
const temporaryLesson = resolve(temporaryModule, '21-04-005-users-and-password-encoding');
rmSync(temporaryModule, { recursive: true, force: true });
mkdirSync(temporaryLesson, { recursive: true });
writeFileSync(resolve(temporaryModule, 'pom.xml'), readFileSync(resolve(moduleRoot, 'pom.xml')));
writeFileSync(resolve(temporaryLesson, 'pom.xml'), readFileSync(resolve(lesson, 'pom.xml')));
cpSync(resolve(lesson, 'src/main'), resolve(temporaryLesson, 'src/main'), { recursive: true });
// 隔离对照版移除显式用户配置，恢复Boot默认用户；正式源码不变。
rmSync(resolve(temporaryLesson, 'src/main/java/cn/ningbingjian/learnjava/security/lesson005/UserConfiguration.java'));

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
console.log('已构建多用户完成版（含后端测试）与默认用户对照版。');
