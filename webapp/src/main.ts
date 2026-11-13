import './app.css'
import './lib/ui/console.css'
import App from './App.svelte'
import { mount } from 'svelte'

const target = document.getElementById('app')
if (!target) throw new Error('#app not found')

mount(App, { target })
