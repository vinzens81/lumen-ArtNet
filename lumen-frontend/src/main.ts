import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
import './app.css';
import { mount } from 'svelte';
import App from './App.svelte';
import { ignorePasswordManagers } from './lib/passwordManagers';

ignorePasswordManagers(document.body);
mount(App, { target: document.getElementById('app')! });
