import {createApp} from 'vue';
import App from './App.vue';
import router from './router';
import './styles/main.css';
createApp(App).use(router).mount('#app');
if ('indexedDB' in globalThis) indexedDB.deleteDatabase('warehouse-photo-uploads');
