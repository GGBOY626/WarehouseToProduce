import {createApp} from 'vue';
import App from './App.vue';
import router from './router';
import './styles/main.css';
import {startPhotoUploadQueue} from './utils/photoUploadQueue';
createApp(App).use(router).mount('#app');
startPhotoUploadQueue();
