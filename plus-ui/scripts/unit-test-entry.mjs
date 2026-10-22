import { createUnitTestPlan } from './unit-test-plan.mjs';
import { runSteps } from './unit-test-runner.mjs';

try {
  process.exitCode = runSteps(createUnitTestPlan(process.argv.slice(2)));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
