/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) KALEIDOS INC
 */
"use strict";

goog.require("cljs.core");
goog.provide("app.common.encoding_impl");

goog.scope(function() {
  const core = cljs.core;
  const global = goog.global;
  const self = app.common.encoding_impl;

  const hexMap = [];
  for (let i = 0; i < 256; i++) {
    hexMap[i] = (i + 0x100).toString(16).substr(1);
  }

  function decodeHex(input, buffer) {
    for (let i = 0; i < input.length; i += 2) {
      view[i / 2] = parseInt(input.substring(i, i + 2), 16);
    }
  }

  function hexToBuffer(input) {
    if (typeof input !== "string") {
      throw new TypeError("Expected input to be a string");
    }

    // Accept UUID hex format
    input = input.replace(/-/g, "");

    if ((input.length % 2) !== 0) {
      throw new RangeError("Expected string to be an even number of characters")
    }

    const view = new Uint8Array(input.length / 2);

    decodeHex(input, view);

    return view.buffer;
  }

  function bufferToHex(source, isUuid) {
    if (source instanceof Uint8Array) {
    } else if (ArrayBuffer.isView(source)) {
      source = new Uint8Array(source.buffer, source.byteOffset, source.byteLength);
    } else if (Array.isArray(source)) {
      source = Uint8Array.from(source);
    }

    if (source.length != 16) {
      throw new RangeError("only 16 bytes array is allowed");
    }

    const spacer = isUuid ? "-" : "";

    let i = 0;
    return  (hexMap[source[i++]] +
             hexMap[source[i++]] +
             hexMap[source[i++]] +
             hexMap[source[i++]] + spacer +
             hexMap[source[i++]] +
             hexMap[source[i++]] + spacer +
             hexMap[source[i++]] +
             hexMap[source[i++]] + spacer +
             hexMap[source[i++]] +
             hexMap[source[i++]] + spacer +
             hexMap[source[i++]] +
             hexMap[source[i++]] +
             hexMap[source[i++]] +
             hexMap[source[i++]] +
             hexMap[source[i++]] +
             hexMap[source[i++]]);
  }

  self.hexToBuffer = hexToBuffer;
  self.bufferToHex = bufferToHex;


  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

  // Use a lookup table to find the index.
  const lookup = typeof Uint8Array === 'undefined' ? [] : new Uint8Array(256);
  for (let i = 0; i < alphabet.length; i++) {
    lookup[alphabet.charCodeAt(i)] = i;
  }

  /*
   * A low-level function that decodes base64 string to a existing Uint8Array
   */

  function decodeBase64_1(source, target) {
    const sourceLength = source.length;
    // const paddingLength = (source[sourceLength - 2] === '=' ? 2 : (source[sourceLength - 1] === '=' ? 1 : 0));
    const baseLength = sourceLength; //(sourceLength - paddingLength) & 0xfffffffc;

    let tmp;
    let i = 0;
    let byteIndex = 0;

    for (; i < baseLength; i += 4) {
      tmp = (lookup[source.charCodeAt(i)] << 18)
        | (lookup[source.charCodeAt(i + 1)] << 12)
        | (lookup[source.charCodeAt(i + 2)] << 6)
        | (lookup[source.charCodeAt(i + 3)]);

      target[byteIndex++] = (tmp >> 16) & 0xFF
      target[byteIndex++] = (tmp >> 8) & 0xFF
      target[byteIndex++] = (tmp) & 0xFF
    }

    // console.log("DECODE END", byteIndex);

    // if (paddingLength === 1) {
    //   tmp = (lookup[source.charCodeAt(i)] << 10)
    //     | (lookup[source.charCodeAt(i + 1)] << 4)
    //     | (lookup[source.charCodeAt(i + 2)] >> 2);

    //   target[byteIndex++] = (tmp >> 8) & 0xFF
    //   target[byteIndex++] = tmp & 0xFF
    // }

    // if (paddingLength === 2) {
    //   tmp = (lookup[source.charCodeAt(i)] << 2) | (lookup[source.charCodeAt(i + 1)] >> 4)
    //   target[byteIndex++] = tmp & 0xFF
    // }
  }

  const decodeBase64_2 = function(data) {
    return Uint8Array.from(atob(data), m => m.charCodeAt(0));
  }

  // const alphabet = Array.from('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_');

  function encodeBase64_1 (buffer) {
    let i;
    let len = buffer.length;
    // let pad = len % 3;
    let result = '';

    for (i = 0; i < len; i += 3) {
      const tmp1 = buffer[i];
      const tmp2 = buffer[i + 1];
      const tmp3 = buffer[i + 2];

      result += alphabet[tmp1 >> 2];
      result += alphabet[((tmp1 & 3) << 4) | (tmp2 >> 4)];
      result += alphabet[((tmp2 & 15) << 2) | (tmp3 >> 6)];
      result += alphabet[tmp3 & 63];
    }

    // This padding causes performance issue
    // if (pad === 2) {
    //   result = result.substring(0, result.length - 1) + '=';
    // } else if (pad === 1) {
    //   result = result.substring(0, result.length - 2) + '==';
    // }

    return result;
  };

  function encodeBase64_2(buffer) {
    let binary = [];
    let byteLength = buffer.byteLength;

    for (let i = 0; i < byteLength; i++) {
      binary.push(String.fromCharCode(buffer[i]));
    }

    return btoa(binary.join(''));
  }

  self.encodeBase64_1 = encodeBase64_1;
  self.encodeBase64_2 = encodeBase64_2;

  self.decodeBase64_1 = decodeBase64_1;
  self.decodeBase64_2 = decodeBase64_2;
});
