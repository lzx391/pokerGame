<template>
  <div class="hello">

  </div>
</template>

<script>
export default {
  name: 'HelloWorld',
  methods: {
    add() {
      this.$store.commit("increment")
    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>

</style>
