import { readFile, mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { chromium } from '@playwright/test'

const file = await readFile(new URL('../../.env', import.meta.url), 'utf8')
const password = file.split(/\r?\n/).find(line => line.startsWith('RELEASE_APPROVER_PASSWORD='))?.split('=')[1]
if (!password) throw new Error('Local approver password is missing.')
const browser = await chromium.launch({ channel: process.env.RELEASE_BROWSER_CHANNEL || undefined })
try {
  const output = new URL('../../docs/screenshots/', import.meta.url)
  await mkdir(output, { recursive: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 }, deviceScaleFactor: 1 })
  await page.goto('http://127.0.0.1:3000')
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.getByText('Flagsmith connected', { exact: true }).waitFor()
  await page.getByText('Checkout cohort health').waitFor()
  await page.screenshot({ path: fileURLToPath(new URL('overview.png', output)), fullPage: true })
  await page.getByRole('button', { name: 'Incidents', exact: true }).click()
  await page.getByText('Policy decision').waitFor()
  await page.screenshot({ path: fileURLToPath(new URL('investigation.png', output)), fullPage: true })
} catch (e) {
  if (e instanceof Error) console.error(e.message, e)
  process.exitCode = 1
} finally {
  await browser.close()
}
