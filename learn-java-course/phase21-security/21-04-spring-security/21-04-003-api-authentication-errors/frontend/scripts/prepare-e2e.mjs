import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const lesson = resolve(frontend, '..');
const moduleRoot = resolve(lesson, '..');
const temporaryModule = resolve(frontend, '.e2e-work/before-contract');
const temporaryLesson = resolve(temporaryModule, '21-04-003-api-authentication-errors');
rmSync(temporaryModule, { recursive: true, force: true });
mkdirSync(temporaryLesson, { recursive: true });
writeFileSync(resolve(temporaryModule, 'pom.xml'), readFileSync(resolve(moduleRoot, 'pom.xml')));
writeFileSync(resolve(temporaryLesson, 'pom.xml'), readFileSync(resolve(lesson, 'pom.xml')));
cpSync(resolve(lesson, 'src/main'), resolve(temporaryLesson, 'src/main'), { recursive: true });
// MVC处理器仍在；比较它能否改变认证阶段的响应。
const configuration = resolve(temporaryLesson, 'src/main/java/cn/ningbingjian/learnjava/security/lesson003/SecurityConfig.java');
// 对照版保留MVC错误处理与业务校验，只恢复上一课的响应配置。
const previous = readFileSync(resolve(frontend, 'tests/fixtures/BeforeContractSecurityConfig.java.txt'), 'utf8');
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

build(lesson, ['clean', 'verify'], 'contract-build');
build(temporaryLesson, ['clean', 'package'], 'before-build');
console.log('已构建认证错误契约完成版（含后端测试）与仅MVC处理器的对照版。');
