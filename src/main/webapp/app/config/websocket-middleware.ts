import SockJS from 'sockjs-client';

import Stomp from 'webstomp-client';
import { Observable } from 'rxjs';

import { websocketActivityMessage } from 'app/modules/administration/administration.reducer';
import { getAccount, logoutServer } from 'app/shared/reducers/authentication';
import cookie from 'app/shared/util/cookie-utils';

let stompClient = null;

let subscriber = null;
let connection: Promise<any>;
let connectedPromise: any = null;
let listener: Observable<any>;
let listenerObserver: any;
let alreadyConnectedOnce = false;

const createConnection = (): Promise<any> => new Promise(resolve => (connectedPromise = resolve));

const createListener = (): Observable<any> =>
  new Observable(observer => {
    listenerObserver = observer;
  });

export const sendActivity = (page: string) => {
  connection?.then(() => {
    stompClient?.send(
      '/topic/activity', // destination
      JSON.stringify({ page }), // body
      {} // header
    );
  });
};

const subscribe = () => {
  connection.then(() => {
    subscriber = stompClient.subscribe('/topic/tracker', data => {
      listenerObserver.next(JSON.parse(data.body));
    });
  });
};

const connect = () => {
  if (connectedPromise !== null || alreadyConnectedOnce) {
    // the connection is already being established
    return;
  }
  connection = createConnection();
  listener = createListener();

  // building absolute path so that websocket doesn't fail when deploying with a context path
  const loc = window.location;
  const baseHref = document.querySelector('base').getAttribute('href').replace(/\/$/, '');

  const headers = {};
  const url = '//' + loc.host + baseHref + '/websocket/tracker';
  headers['X-XSRF-TOKEN'] = cookie.read('XSRF-TOKEN');
  const socket = new SockJS(url);
  stompClient = Stomp.over(socket, { protocols: ['v12.stomp'] });

  stompClient.connect(headers, () => {
    connectedPromise('success');
    connectedPromise = null;
    sendActivity(window.location.pathname);
    alreadyConnectedOnce = true;
  });
};

const disconnect = () => {
  if (stompClient !== null) {
    if (stompClient.connected) {
      stompClient.disconnect();
    }
    stompClient = null;
  }
  alreadyConnectedOnce = false;
};

const receive = () => listener;

const unsubscribe = () => {
  if (subscriber !== null) {
    subscriber.unsubscribe();
  }
  listener = createListener();
};

export default store => next => action => {
  if (getAccount.fulfilled.match(action)) {
    connect();
    const isAdmin = action.payload.data.authorities.includes('ROLE_ADMIN');
    if (!alreadyConnectedOnce && isAdmin) {
      subscribe();
      receive().subscribe(activity => {
        return store.dispatch(websocketActivityMessage(activity));
      });
    }
  } else if (getAccount.rejected.match(action) || logoutServer.pending.match(action)) {
    unsubscribe();
    disconnect();
  }
  return next(action);
};
