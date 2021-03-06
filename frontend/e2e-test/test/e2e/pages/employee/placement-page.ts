// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Selector, t } from 'testcafe'

export default class PlacementPage {
  readonly list: Selector = Selector('[data-qa="placement-list"]').find(
    '[data-qa="placement-item"]'
  )

  async placeIn(placementNumber: number) {
    const unit: Selector = this.list
      .nth(placementNumber)
      .find('[data-qa="select-placement-unit"]')
    await t.click(unit)
  }

  async sendPlacement() {
    const button: Selector = Selector('[data-qa="send-placement-button"')
    await t.click(button)
  }
}
