import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const lesson = resolve(frontend, '..');
const moduleRoot = resolve(lesson, '..');
const temporaryModule = resolve(frontend, '.e2e-work/before-security');
const temporaryLesson = resolve(temporaryModule, '21-04-001-hello-spring-security');
rmSync(temporaryModule, { recursive: true, force: true });
mkdirSync(temporaryLesson, { recursive: true });
writeFileSync(resolve(temporaryModule, 'pom.xml'), readFileSync(resolve(moduleRoot, 'pom.xml')));
const finalPom = readFileSync(resolve(lesson, 'pom.xml'), 'utf8');
const securityDependency = /\s*<dependency>\s*<groupId>org\.springframework\.boot<\/groupId>\s*<artifactId>spring-boot-starter-security<\/artifactId>\s*<\/dependency>/g;
if ([...finalPom.matchAll(securityDependency)].length !== 1) {
  throw new Error('第一课必须恰好声明一处安全依赖，才能建立无安全依赖的对照工程。');
}
writeFileSync(resolve(temporaryLesson, 'pom.xml'), finalPom.replace(securityDependency, ''));
cpSync(resolve(lesson, 'src/main'), resolve(temporaryLesson, 'src/main'), { recursive: true });

function build(cwd, args, name) {
  const result = spawnSync(process.env.MAVEN_CMD || 'mvn', ['-B', '-ntp', ...args], {
    cwd, encoding: 'utf8', timeout: 240000,
  });
  writeFileSync(resolve(frontend, `.e2e-work/${name}.log`), `${result.stdout || ''}\n${result.stderr || ''}`);
  if (result.error || result.status !== 0) {
    throw new Error(`${name}构建失败，请检查 .e2e-work/${name}.log 及 mvn -v 显示的JDK。`);
  }
}

build(lesson, ['clean', 'verify'], 'secured-build');
build(temporaryLesson, ['clean', 'package'], 'before-build');
console.log('已构建完成版（含后端测试）与未加安全依赖的对照版。');
