import { inject } from '@angular/core';
import { provideApollo } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { ApolloLink, InMemoryCache, split } from '@apollo/client/core';
import { setContext } from '@apollo/client/link/context';
import { HttpHeaders } from '@angular/common/http';
import { environment } from '../environments/environment';
import { TokenStorageAdapter } from './core/services/token-storage.adapter';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { createClient } from 'graphql-ws';
import { getMainDefinition } from '@apollo/client/utilities';

export const graphqlProvider = provideApollo(() => {
  const httpLink = inject(HttpLink);
  const tokenStorage = inject(TokenStorageAdapter);

  const http = httpLink.create({
    uri: environment.graphqlEndpoint
  });

  const auth = setContext((_, { headers }) => {
    // Guard against SSR: localStorage is only available in the browser
    if (typeof window === 'undefined') {
      return {};
    }

    const token = tokenStorage.getToken();
    if (!token) {
      return {};
    }

    let authHeaders = headers instanceof HttpHeaders ? headers : new HttpHeaders(headers ?? {});
    authHeaders = authHeaders.set('Authorization', `Bearer ${token}`);

    return {
      headers: authHeaders
    };
  });

  // Create WebSocket link for subscriptions (only in browser context)
  let wsLink: GraphQLWsLink | null = null;
  if (typeof window !== 'undefined') {
    // Parse HTTP/HTTPS endpoint and convert to WS/WSS with /graphql-ws path
    const url = new URL(environment.graphqlEndpoint);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.pathname = url.pathname.replace('/graphql', '') || '/';
    url.pathname = (url.pathname === '/' ? '' : url.pathname) + '/graphql-ws';
    const wsEndpoint = url.toString();
    
    wsLink = new GraphQLWsLink(createClient({
      url: wsEndpoint,
      connectionParams: () => {
        const token = tokenStorage.getToken();
        return token ? { Authorization: `Bearer ${token}` } : {};
      },
    } as any));
  }

  // Split link: use WebSocket for subscriptions if available, HTTP otherwise
  const link = wsLink ? split(
    ({ query }) => {
      const definition = getMainDefinition(query);
      return definition.kind === 'OperationDefinition' && definition.operation === 'subscription';
    },
    wsLink,
    auth.concat(http)
  ) : auth.concat(http);

  return {
    link,
    cache: new InMemoryCache(),
    defaultOptions: {
      watchQuery: {
        fetchPolicy: 'cache-and-network',
        errorPolicy: 'all'
      },
      query: {
        fetchPolicy: 'cache-first',
        errorPolicy: 'all'
      },
      mutate: {
        errorPolicy: 'all'
      }
    }
  };
});
