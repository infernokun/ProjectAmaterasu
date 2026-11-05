export type RequestStatus = 'idle' | 'loading' | 'loaded' | 'error';

/** Shared slice for stores that load from the API. */
export interface RequestState {
  status: RequestStatus;
  error: string | null;
}

export const initialRequestState: RequestState = { status: 'idle', error: null };
