<!--
SPDX-FileCopyrightText: 2017-2020 City of Espoo

SPDX-License-Identifier: LGPL-2.1-or-later
-->

<template>
  <main>
    <section class="section container application-preview-wrapper">
      <c-title :size="2" :isGreyDark="true">{{
        $t('form.summary.club-title')
      }}</c-title>
      <div class="application-date-details">
        <span class="application-created">
          <span class="strong"
            >{{ $t('form.summary.application-created') }}:</span
          >
          {{ applicationForm.createdDate | date }}
        </span>
        <span class="application-modified">
          <span class="strong"
            >{{ $t('form.summary.application-last-updated') }}:</span
          >
          {{ applicationForm.modifiedDate | date }}
        </span>
      </div>
      <div class="application-preview">
        <club-summary
          :applicationForm="applicationForm"
        />
      </div>

      <div class="buttons has-text-centered">
        <button class="button  is-primary is-outlined" @click="goBack">
          Takaisin
        </button>
      </div>
    </section>
  </main>
</template>

<script>
  import moment from 'moment'
  import { mapGetters } from 'vuex'
  import ClubSummary from '@/views/applications/club-application/form-components/summary/application-club-summary'

  export default {
    components: {
      ClubSummary
    },
    computed: {
      ...mapGetters(['applicationForm']),
      id() {
        return this.$route.params.id
      }
    },
    methods: {
      async loadUnits() {
        // the value is in "UI format"
        const date = moment(
          this.applicationForm.preferredStartDate,
          'DD.MM.YYYY'
        ).format('YYYY-MM-DD')
        const type = 'CLUB'
        this.$store.dispatch('loadApplicationUnits', { type, date })
      },
      goBack() {
        return history.go(-1)
      }
    },
    async created() {
      await this.$store.dispatch('loadApplication', {
        type: this.applicationForm.type.value,
        applicationId: this.id
      })
      this.loadUnits()
    }
  }
</script>

<style lang="scss" scoped>
  .application-preview-wrapper {
    .title.is-2 {
      margin-bottom: 2rem;
    }
  }

  .application-preview {
    border: 1px solid #cecece;
    border-radius: 3px;
    margin-bottom: 2rem;

    strong {
      margin-right: 0.25rem;
    }
  }

  .application-date-details {
    margin-bottom: 0.5rem;
    font-size: 0.925rem;
    padding-left: 2.2rem;

    .application-created {
      margin-right: 2rem;
    }
  }
</style>
