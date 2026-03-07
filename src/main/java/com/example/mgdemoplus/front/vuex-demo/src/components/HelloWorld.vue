<template>
  <div class="hello">
{{this.$store.state.count}}
    <button @click="add">+1</button>
  </div>
</template>

<script>
export default {
  name: 'HelloWorld',
methods:{
    add(){
      this.$store.state.count+=1
    }
}
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>

</style>
