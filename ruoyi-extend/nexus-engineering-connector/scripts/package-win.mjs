import { cliEntries, writeDistribution } from './distribution.mjs';
/** 保留原CLI分发入口；桌面应用另由package:desktop生成。 */
console.log(JSON.stringify(await writeDistribution(await cliEntries(), 'nexus-engineering-connector-0.1.0-win.zip')));
