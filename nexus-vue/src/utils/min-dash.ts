/* eslint-disable no-func-assign */
/**
 * Flatten array, one level deep.
 *
 * @param {Array<?>} arr
 *
 * @return {Array<?>}
 */
export function flatten<T>(arr: T[][]): T[] {
  return Array.prototype.concat.apply([], arr);
}

const nativeToString = Object.prototype.toString;
const nativeHasOwnProperty = Object.prototype.hasOwnProperty;

export function isUndefined(obj: any): obj is undefined {
  return obj === undefined;
}

export function isDefined(obj: any): boolean {
  return obj !== undefined;
}

export function isNil(obj: any): obj is null | undefined {
  return obj == null;
}

export function isArray(obj: any): obj is any[] {
  return nativeToString.call(obj) === '[object Array]';
}

export function isObject(obj: any): obj is Record<string, any> {
  return nativeToString.call(obj) === '[object Object]';
}

export function isNumber(obj: any): obj is number {
  return nativeToString.call(obj) === '[object Number]';
}

export function isFunction(obj: any): obj is Function {
  const tag = nativeToString.call(obj);
  return tag === '[object Function]' || tag === '[object AsyncFunction]' || tag === '[object GeneratorFunction]' || tag === '[object AsyncGeneratorFunction]' || tag === '[object Proxy]';
}

export function isString(obj: any): obj is string {
  return nativeToString.call(obj) === '[object String]';
}

/**
 * Ensure collection is an array.
 *
 * @param {Object} obj
 */
export function ensureArray(obj: any): void {
  if (isArray(obj)) {
    return;
  }
  throw new Error('must supply array');
}

/**
 * Return true, if target owns a property with the given key.
 *
 * @param {Object} target
 * @param {String} key
 *
 * @return {Boolean}
 */
export function has(target: any, key: string | number): boolean {
  return nativeHasOwnProperty.call(target, key);
}

/**
 * Find element in collection.
 *
 * @param  {Array|Object} collection
 * @param  {Function|Object} matcher
 *
 * @return {Object}
 */
export function find<T>(collection: T[] | Record<string, T>, matcher: ((val: T, key: string | number) => boolean) | T): T | undefined {
  matcher = toMatcher(matcher);
  let match: T | undefined;
  forEach(collection, function(val, key) {
    if (matcher(val, key)) {
      match = val;
      return false;
    }
  });
  return match;
}

/**
 * Find element index in collection.
 *
 * @param  {Array|Object} collection
 * @param  {Function} matcher
 *
 * @return {Object}
 */
export function findIndex<T>(collection: T[], matcher: ((val: T, key: number) => boolean) | T): number {
  matcher = toMatcher(matcher);
  let idx = -1;
  forEach(collection, function(val, key) {
    if (matcher(val, key)) {
      idx = key as number;
      return false;
    }
  });
  return idx;
}

/**
 * Find element in collection.
 *
 * @param  {Array|Object} collection
 * @param  {Function} matcher
 *
 * @return {Array} result
 */
export function filter<T>(collection: T[] | Record<string, T>, matcher: ((val: T, key: string | number) => boolean) | T): T[] {
  const result: T[] = [];
  forEach(collection, function(val, key) {
    if (matcher(val, key)) {
      result.push(val);
    }
  });
  return result;
}

/**
 * Iterate over collection; returning something
 * (non-undefined) will stop iteration.
 *
 * @param  {Array|Object} collection
 * @param  {Function} iterator
 *
 * @return {Object} return result that stopped the iteration
 */
export function forEach<T, R>(collection: T[] | Record<string, T>, iterator: (val: T, key: string | number) => R | false): R | undefined {
  let val: T;
  let result: R | undefined;

  if (isUndefined(collection)) {
    return;
  }

  const convertKey = isArray(collection) ? toNum : identity;

  for (const key in collection) {
    if (has(collection, key)) {
      val = collection[key];
      result = iterator(val, convertKey(key));

      if (result === false) {
        return val;
      }
    }
  }
}

/**
 * Return collection without element.
 *
 * @param  {Array} arr
 * @param  {Function} matcher
 *
 * @return {Array}
 */
export function without<T>(arr: T[], matcher: ((el: T, idx: number) => boolean) | T): T[] {
  if (isUndefined(arr)) {
    return [];
  }

  ensureArray(arr);
  const matcherFn = toMatcher(matcher);
  return arr.filter(function(el, idx) {
    return !matcherFn(el, idx);
  });
}

/**
 * Reduce collection, returning a single result.
 *
 * @param  {Object|Array} collection
 * @param  {Function} iterator
 * @param  {Any} result
 *
 * @return {Any} result returned from last iterator
 */
export function reduce<T, R>(collection: T[] | Record<string, T>, iterator: (result: R, value: T, idx: string | number) => R, result: R): R {
  forEach(collection, function(value, idx) {
    result = iterator(result, value, idx);
  });
  return result;
}

/**
 * Return true if every element in the collection
 * matches the criteria.
 *
 * @param  {Object|Array} collection
 * @param  {Function} matcher
 *
 * @return {Boolean}
 */
export function every<T>(collection: T[] | Record<string, T>, matcher: ((val: T, key: string | number) => boolean) | T): boolean {
  return !!reduce(collection, function(matches, val, key) {
    return matches && matcher(val, key);
  }, true);
}

/**
 * Return true if some elements in the collection
 * match the criteria.
 *
 * @param  {Object|Array} collection
 * @param  {Function} matcher
 *
 * @return {Boolean}
 */
export function some<T>(collection: T[] | Record<string, T>, matcher: ((val: T, key: string | number) => boolean) | T): boolean {
  return !!find(collection, matcher);
}

/**
 * Transform a collection into another collection
 * by piping each member through the given fn.
 *
 * @param  {Object|Array}   collection
 * @param  {Function} fn
 *
 * @return {Array} transformed collection
 */
export function map<T, R>(collection: T[] | Record<string, T>, fn: (val: T, key: string | number) => R): R[] {
  const result: R[] = [];
  forEach(collection, function(val, key) {
    result.push(fn(val, key));
  });
  return result;
}

/**
 * Get the collections keys.
 *
 * @param  {Object|Array} collection
 *
 * @return {Array}
 */
export function keys(collection: any): string[] {
  return collection && Object.keys(collection) || [];
}

/**
 * Shorthand for `keys(o).length`.
 *
 * @param  {Object|Array} collection
 *
 * @return {Number}
 */
export function size(collection: any): number {
  return keys(collection).length;
}

/**
 * Get the values in the collection.
 *
 * @param  {Object|Array} collection
 *
 * @return {Array}
 */
export function values<T>(collection: T[] | Record<string, T>): T[] {
  return map(collection, function(val) {
    return val;
  });
}

/**
 * Group collection members by attribute.
 *
 * @param  {Object|Array} collection
 * @param  {Function} extractor
 *
 * @return {Object} map with { attrValue => [ a, b, c ] }
 */
export function groupBy<T>(collection: T[], extractor: ((val: T) => any) | string, grouped?: Record<string, T[]>): Record<string, T[]> {
  grouped = grouped || {};
  const extractorFn = toExtractor(extractor);
  forEach(collection, function(val) {
    const discriminator = extractorFn(val) || '_';
    let group = grouped[discriminator];

    if (!group) {
      group = grouped[discriminator] = [];
    }

    group.push(val);
  });
  return grouped;
}

export function uniqueBy<T>(extractor: ((val: T) => any) | string, ...collections: T[][]): T[] {
  const extractorFn = toExtractor(extractor);
  const grouped: Record<string, T[]> = {};

  forEach(collections, function(c) {
    return groupBy(c, extractorFn, grouped);
  });

  const result = map(grouped, function(val) {
    return val[0];
  });
  return result;
}

export const unionBy = uniqueBy;

/**
 * Sort collection by criteria.
 *
 * @param  {Object|Array} collection
 * @param  {String|Function} extractor
 *
 * @return {Array}
 */
export function sortBy<T>(collection: T[], extractor: ((val: T, key: number) => any) | string): T[] {
  const extractorFn = toExtractor(extractor);
  const sorted: Array<{ d: any; v: T }> = [];

  forEach(collection, function(value, key) {
    const disc = extractorFn(value, key as number);
    const entry = {
      d: disc,
      v: value
    };

    for (let idx = 0; idx < sorted.length; idx++) {
      const d = sorted[idx].d;

      if (disc < d) {
        sorted.splice(idx, 0, entry);
        return;
      }
    }

    sorted.push(entry);
  });

  return map(sorted, function(e) {
    return e.v;
  });
}

/**
 * Create an object pattern matcher.
 *
 * @example
 *
 * const matcher = matchPattern({ id: 1 });
 *
 * let element = find(elements, matcher);
 *
 * @param  {Object} pattern
 *
 * @return {Function} matcherFn
 */
export function matchPattern<T>(pattern: Partial<T>): (el: T) => boolean {
  return function(el) {
    return every(pattern, function(val, key) {
      return el[key as keyof T] === val;
    });
  };
}

function toExtractor<T>(extractor: ((val: T) => any) | string): (val: T) => any {
  return isFunction(extractor) ? extractor : function(e) {
    return e[extractor as keyof T];
  };
}

function toMatcher<T>(matcher: ((val: T, key: string | number) => boolean) | T): (val: T, key: string | number) => boolean {
  return isFunction(matcher) ? matcher : function(e) {
    return e === matcher;
  };
}

function identity<T>(arg: T): T {
  return arg;
}

function toNum(arg: string | number): number {
  return Number(arg);
}

/**
 * Debounce fn, calling it only once if the given time
 * elapsed between calls.
 *
 * Lodash-style the function exposes methods to `#clear`
 * and `#flush` to control internal behavior.
 *
 * @param  {Function} fn
 * @param  {Number} timeout
 *
 * @return {Function} debounced function
 */
export interface DebouncedFunction<T extends (...args: any[]) => any> extends Function {
  (...args: Parameters<T>): void;
  flush(): void;
  cancel(): void;
}

export function debounce<T extends (...args: any[]) => any>(fn: T, timeout: number): DebouncedFunction<T> {
  let timer: number | undefined;
  let lastArgs: Parameters<T> | undefined;
  let lastThis: any;
  let lastNow: number | undefined;

  function fire(force: boolean): void {
    const now = Date.now();
    const scheduledDiff = force ? 0 : (lastNow! + timeout) - now;

    if (scheduledDiff > 0) {
      return schedule(scheduledDiff);
    }

    fn.apply(lastThis, lastArgs!);
    clear();
  }

  function schedule(timeout: number): void {
    timer = setTimeout(fire, timeout) as unknown as number;
  }

  function clear(): void {
    if (timer) {
      clearTimeout(timer);
    }

    timer = lastNow = lastArgs = lastThis = undefined;
  }

  function flush(): void {
    if (timer) {
      fire(true);
    }

    clear();
  }

  const callback = function(this: any, ...args: Parameters<T>): void {
    lastNow = Date.now();
    lastArgs = args;
    lastThis = this;

    if (!timer) {
      schedule(timeout);
    }
  } as DebouncedFunction<T>;

  callback.flush = flush;
  callback.cancel = clear;
  return callback;
}

/**
 * Throttle fn, calling at most once
 * in the given interval.
 *
 * @param  {Function} fn
 * @param  {Number} interval
 *
 * @return {Function} throttled function
 */
export function throttle<T extends (...args: any[]) => any>(fn: T, interval: number): (...args: Parameters<T>) => void {
  let throttling = false;
  return function(this: any, ...args: Parameters<T>) {
    if (throttling) {
      return;
    }

    fn.apply(this, args);
    throttling = true;
    setTimeout(function() {
      throttling = false;
    }, interval);
  };
}

/**
 * Bind function against target <this>.
 *
 * @param  {Function} fn
 * @param  {Object}   target
 *
 * @return {Function} bound function
 */
export function bind<T extends (...args: any[]) => any>(fn: T, target: any): T {
  return fn.bind(target);
}

/**
 * Convenience wrapper for `Object.assign`.
 *
 * @param {Object} target
 * @param {...Object} others
 *
 * @return {Object} the target
 */
export function assign<T extends Record<string, any>>(target: T, ...others: Partial<T>[]): T {
  return Object.assign(target, ...others);
}

/**
 * Sets a nested property of a given object to the specified value.
 *
 * This mutates the object and returns it.
 *
 * @param {Object} target The target of the set operation.
 * @param {(string|number)[]} path The path to the nested value.
 * @param {any} value The value to set.
 */
export function set<T>(target: T, path: (string | number)[], value: any): T {
  let currentTarget: any = target;
  forEach(path, function(key, idx) {
    if (typeof key !== 'number' && typeof key !== 'string') {
      throw new Error('illegal key type: ' + typeof key + '. Key should be of type number or string.');
    }

    if (key === 'constructor') {
      throw new Error('illegal key: constructor');
    }

    if (key === '__proto__') {
      throw new Error('illegal key: __proto__');
    }

    const nextKey = path[idx + 1];
    let nextTarget = currentTarget[key];

    if (isDefined(nextKey) && isNil(nextTarget)) {
      nextTarget = currentTarget[key] = isNaN(+nextKey) ? {} : [];
    }

    if (isUndefined(nextKey)) {
      if (isUndefined(value)) {
        delete currentTarget[key];
      } else {
        currentTarget[key] = value;
      }
    } else {
      currentTarget = nextTarget;
    }
  });
  return target;
}

/**
 * Gets a nested property of a given object.
 *
 * @param {Object} target The target of the get operation.
 * @param {(string|number)[]} path The path to the nested value.
 * @param {any} [defaultValue] The value to return if no value exists.
 */
export function get<T = any>(target: any, path: (string | number)[], defaultValue?: T): T {
  let currentTarget: any = target;
  forEach(path, function(key) {
    if (isNil(currentTarget)) {
      currentTarget = undefined;
      return false;
    }

    currentTarget = currentTarget[key];
  });
  return isUndefined(currentTarget) ? defaultValue! : currentTarget;
}

/**
 * Pick given properties from the target object.
 *
 * @param {Object} target
 * @param {Array} properties
 *
 * @return {Object} target
 */
export function pick<T extends Record<string, any>, K extends keyof T>(target: T, properties: K[]): Pick<T, K> {
  const result: Partial<Pick<T, K>> = {};
  const obj = Object(target);
  forEach(properties, function(prop) {
    if (prop in obj) {
      result[prop] = target[prop];
    }
  });
  return result as Pick<T, K>;
}

/**
 * Pick all target properties, excluding the given ones.
 *
 * @param {Object} target
 * @param {Array} properties
 *
 * @return {Object} target
 */
export function omit<T extends Record<string, any>, K extends keyof T>(target: T, properties: K[]): Omit<T, K> {
  const result: Partial<Omit<T, K>> = {};
  const obj = Object(target);
  forEach(obj, function(prop, key) {
    if (properties.indexOf(key as K) === -1) {
      result[key as string] = prop;
    }
  });
  return result as Omit<T, K>;
}

/**
 * Recursively merge `...sources` into given target.
 *
 * Does support merging objects; does not support merging arrays.
 *
 * @param {Object} target
 * @param {...Object} sources
 *
 * @return {Object} the target
 */
export function merge<T extends Record<string, any>>(target: T, ...sources: Partial<T>[]): T {
  if (!sources.length) {
    return target;
  }

  forEach(sources, function(source) {
    if (!source || !isObject(source)) {
      return;
    }

    forEach(source, function(sourceVal, key) {
      if (key === '__proto__') {
        return;
      }

      const targetVal = target[key];

      if (isObject(sourceVal)) {
        if (!isObject(targetVal)) {
          targetVal = {} as any;
        }

        target[key] = merge(targetVal, sourceVal);
      } else {
        target[key] = sourceVal;
      }
    });
  });

  return target;
}
