import { DestroyRef, inject, signal } from '@angular/core';

/**
 * Counts the seconds of a long request (the local AI takes 15-35 s), so the user sees it is still working.
 * Call it in a field initializer: it stops by itself when the component is destroyed.
 */
export function elapsedTimer() {
  const seconds = signal(0);
  let handle: ReturnType<typeof setInterval> | undefined;

  const stop = () => {
    clearInterval(handle);
    handle = undefined;
  };
  inject(DestroyRef).onDestroy(stop);

  return {
    seconds: seconds.asReadonly(),
    start: () => {
      stop();
      seconds.set(0);
      handle = setInterval(() => seconds.update(s => s + 1), 1000);
    },
    stop,
  };
}
