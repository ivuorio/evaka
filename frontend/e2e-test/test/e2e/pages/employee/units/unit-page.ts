// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { ClientFunction, Selector, t } from 'testcafe'
import config from '../../../config'
import { UUID } from '../../../dev-api/types'

export default class UnitPage {
  private readonly baseUrl = config.employeeUrl

  readonly unitName = Selector('[data-qa="unit-name"]')
  readonly visitingAddress = Selector('[data-qa="unit-visiting-address"]')
  readonly missingPlacementRows = Selector('[data-qa="missing-placement-row"]')
  readonly groups = Selector('[data-qa="daycare-group-collapsible"]')

  readonly groupRenameModal = {
    input: Selector('[data-qa="group-rename-modal"] [data-qa="name-input"]'),
    submit: Selector('[data-qa="group-rename-modal"] [data-qa="modal-okBtn"]')
  }

  async navigateHere(id: string) {
    await t.navigateTo(`${this.baseUrl}/units/${id}`)
  }

  async openTabUnitInfo() {
    await t.click(Selector('[data-qa="unit-info-tab"]'))
  }

  async openTabGroups() {
    await t.click(Selector('[data-qa="groups-tab"]'))
  }

  async openTabWaitingConfirmation() {
    await t.click(Selector('[data-qa="waiting-confirmation-tab"]'))
  }

  async openTabPlacementProposals() {
    await t.click(Selector('[data-qa="placement-proposals-tab"]'))
  }

  async openTabApplications() {
    await t.click(Selector('[data-qa="applications-tab"]'))
  }

  async selectPeriodYear() {
    await t.click(Selector('[data-qa="unit-filter-period-1-year"]'))
  }

  async openGroups() {
    if (
      (await Selector('[data-qa="groups-title-bar"]').getAttribute(
        'data-status'
      )) === 'closed'
    ) {
      await t.click(Selector('[data-qa="toggle-all-groups-collapsible"]'))
    }
  }

  readonly occupancies = (type: 'confirmed' | 'planned') => ({
    maximum: Selector(`[data-qa="occupancies-maximum-${type}"]`),
    minimum: Selector(`[data-qa="occupancies-minimum-${type}"]`),
    noValidValues: Selector(`[data-qa="occupancies-no-valid-values-${type}"]`)
  })

  readonly supervisorAcl = new AclTable(
    Selector('[data-qa="daycare-acl-supervisors"]')
  )
  readonly staffAcl = new AclTable(Selector('[data-qa="daycare-acl-staff"]'))

  readonly placementProposalsAcceptButton = Selector(
    '[data-qa="placement-proposals-accept-button"]'
  )

  async clickProposalAccept(applicationId: string) {
    const button = Selector(
      `[data-qa="placement-proposal-row-${applicationId}"]`
    ).find('[data-qa="accept-button"]')
    await t.expect(button.visible).ok()
    await t.click(button)
  }

  async clickProposalReject(applicationId: string) {
    const button = Selector(
      `[data-qa="placement-proposal-row-${applicationId}"]`
    ).find('[data-qa="reject-button"]')
    await t.expect(button.visible).ok()
    await t.click(button)
  }

  async selectProposalRejectionReason(n: number) {
    const radios = Selector('[data-qa="proposal-reject-reason"]')
    await t.click(radios.nth(n))
  }

  async submitProposalRejectionReason() {
    await t.click(Selector('[data-qa="modal-okBtn"]'))
  }

  readonly waitingGuardianConfirmationRow = Selector(
    '[data-qa="placement-plan-row"]'
  )
}

class AclTable {
  constructor(readonly root: Selector) {}

  readonly table = this.root.find('[data-qa="acl-table"]')
  readonly rows = this.root.find('[data-qa="acl-row"]')
  readonly addInput = this.root.find('.acl-select')
  readonly addButton = this.root.find('[data-qa="acl-add-button"]')
  readonly deleteModal = Selector('[data-qa="modal"]')
  readonly deleteModalOk = this.deleteModal.find('[data-qa="modal-okBtn"]')

  async waitUntilLoaded() {
    await t.expect(this.table.visible).ok()
  }
  async addEmployeeAcl(employeeId: UUID) {
    await this.waitUntilLoaded()
    // typing text would be better, but it's buggy with react-select
    await t.click(this.addInput)
    await t.click(this.root.find(`[data-qa="value-${employeeId}"]`))
    await t.click(this.addButton)
    await this.waitUntilLoaded()
  }
  async getAclRows(): Promise<Array<{ name: string; email: string }>> {
    await this.waitUntilLoaded()
    const table = this.table
    return ClientFunction(
      () => {
        const rows = Array.from(
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          ((table() as any) as HTMLElement).querySelectorAll(
            '[data-qa="acl-row"]'
          )
        )
        return rows.map((row) => ({
          name: row.querySelector('[data-qa="name"]')?.textContent ?? '',
          email: row.querySelector('[data-qa="email"]')?.textContent ?? ''
        }))
      },
      { dependencies: { table } }
    )()
  }
  async deleteEmployeeAclByIndex(index: number) {
    await this.waitUntilLoaded()
    await t.click(this.rows.nth(index).find('[data-qa="delete"]'))
    await t.expect(this.deleteModal.visible).ok()
    await t.click(this.deleteModalOk)
    await t.expect(this.deleteModal.exists).notOk()
    await this.waitUntilLoaded()
  }
}

export const missingPlacementElement = (root: Selector) => ({
  root,
  childName: root.find('[data-qa="child-name"]'),
  childDateOfBirth: root.find('[data-qa="child-dob"]'),
  placementDuration: root.find('[data-qa="placement-duration"]'),
  groupMissingDuration: root.find('[data-qa="group-missing-duration"]'),
  addToGroupBtn: root.find('[data-qa="add-to-group-btn"]'),
  addToGroup: async () => {
    await t.click(root.find('[data-qa="add-to-group-btn"]'))
  }
})

export const daycareGroupElement = (root: Selector) => ({
  root,
  groupName: root.find('[data-qa="group-name"]'),
  groupFounded: root.find('[data-qa="group-founded"]'),
  noChildrenPlaceholder: root.find('[data-qa="no-children-placeholder"]'),
  groupPlacementRows: root.find('[data-qa="group-placement-row"]'),
  groupRenameBtn: root.find('[data-qa="btn-rename-group"]')
})

export const daycareGroupPlacementElement = (root: Selector) => ({
  root,
  childName: root.find('[data-qa="child-name"]'),
  placementDuration: root.find('[data-qa="placement-duration"]'),
  remove: async () => {
    await t.click(root.find('[data-qa="remove-btn"]'))
  }
})
