<template>
  <div id="app">
<!--    {{ this.$store.state.count }}-->
    {{count}}
    <button @click="add">+1</button>
  </div>
</template>

<script>


import HelloWorld from "@/components/HelloWorld.vue";

export default {
  name: 'App',
  components: {

  },
  computed:{
    count(){
      return  this.$store.state.count
    }
  },
  methods: {
    add() {
      this.$store.commit("increment")
    }
  }
}
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
