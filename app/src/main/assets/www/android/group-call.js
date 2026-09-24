// Weft group call (Phase 4): WebRTC mesh for up to 6 (voice) or 4 (video), one RTCPeerConnection
// per other participant, always through the user's own TURN servers (relay only), every media frame
// encrypted with the call's key using SimpleX's callCrypto (../call.js). Signalling goes through
// the app, which carries it over the group chat.
//
// App -> page: processGroupCommand({type, ...})
//   start {media, aesKey, iceServers}  · offerTo {peer}  · signal {peer, data}  · peerLeft {peer}
//   media {source: "mic"|"camera", enable}  · leave
// Page -> app (sendMessageToNative): ready · signal {peer, data} · state {peer, state} · error {message}
"use strict";
const weftGroup = (() => {
  let local = null, key = null, config = null, video = false;
  const peers = new Map(); // peer id -> {pc, pending: [], video element}
  const send = (msg) => sendMessageToNative(msg);
  const pack = (x) => LZString.compressToBase64(JSON.stringify(x));
  const unpack = (s) => JSON.parse(LZString.decompressFromBase64(s));
  const encrypted = () => key && "createEncodedStreams" in RTCRtpSender.prototype;

  function transform(endpoint, operation) {
    if (!encrypted()) return;
    const { readable, writable } = endpoint.createEncodedStreams();
    readable.pipeThrough(new TransformStream({ transform: callCrypto.transformFrame[operation](key) })).pipeTo(writable);
  }

  function peer(id) {
    let p = peers.get(id);
    if (p) return p;
    const pc = new RTCPeerConnection({
      iceServers: config.iceServers,
      iceTransportPolicy: "relay", // never this phone's own address
      encodedInsertableStreams: !!key,
      sdpSemantics: "unified-plan",
    });
    p = { pc, pending: [], el: null };
    peers.set(id, p);
    for (const t of local.getTracks()) transform(pc.addTrack(t, local), "encrypt");
    pc.onicecandidate = ({ candidate }) => candidate && send({ type: "signal", peer: id, data: { kind: "ice", ice: pack([candidate]) } });
    pc.onconnectionstatechange = () => send({ type: "state", peer: id, state: pc.connectionState });
    pc.ontrack = (e) => {
      transform(e.receiver, "decrypt");
      if (!p.el) {
        p.el = document.createElement(e.track.kind === "video" ? "video" : "audio");
        p.el.autoplay = true; p.el.playsInline = true;
        (e.track.kind === "video" ? document.getElementById("grid") : document.body).appendChild(p.el);
      }
      p.el.srcObject = e.streams[0] || new MediaStream([e.track]);
    };
    return p;
  }

  async function flush(p) { for (const c of p.pending.splice(0)) await p.pc.addIceCandidate(c).catch(() => {}); }

  async function onSignal(id, data) {
    if (data.kind === "offer") {
      const p = peer(id);
      await p.pc.setRemoteDescription(unpack(data.sdp));
      await flush(p);
      const answer = await p.pc.createAnswer();
      await p.pc.setLocalDescription(answer);
      send({ type: "signal", peer: id, data: { kind: "answer", sdp: pack(p.pc.localDescription) } });
    } else if (data.kind === "answer") {
      const p = peers.get(id); if (!p) return;
      await p.pc.setRemoteDescription(unpack(data.sdp));
      await flush(p);
    } else if (data.kind === "ice") {
      const p = peer(id);
      for (const c of unpack(data.ice)) {
        if (p.pc.remoteDescription) await p.pc.addIceCandidate(c).catch(() => {}); else p.pending.push(c);
      }
    }
  }

  function close(id) {
    const p = peers.get(id); if (!p) return;
    p.pc.close(); p.el && p.el.remove(); peers.delete(id);
  }

  return async function (cmd) {
    try {
      switch (cmd.type) {
        case "start":
          config = { iceServers: cmd.iceServers || [] }; // no defaults: only the user's servers
          video = cmd.media === "video";
          key = cmd.aesKey ? await callCrypto.decodeAesKey(cmd.aesKey) : null;
          local = await navigator.mediaDevices.getUserMedia({ audio: true, video: video ? { facingMode: "user" } : false });
          send({ type: "ready", encrypted: !!encrypted() });
          break;
        case "offerTo": {
          const p = peer(cmd.peer);
          const offer = await p.pc.createOffer();
          await p.pc.setLocalDescription(offer);
          send({ type: "signal", peer: cmd.peer, data: { kind: "offer", sdp: pack(p.pc.localDescription) } });
          break;
        }
        case "signal": await onSignal(cmd.peer, cmd.data); break;
        case "peerLeft": close(cmd.peer); break;
        case "media":
          for (const t of local ? local.getTracks() : []) {
            if ((cmd.source === "mic" && t.kind === "audio") || (cmd.source === "camera" && t.kind === "video")) t.enabled = cmd.enable;
          }
          break;
        case "leave":
          for (const id of [...peers.keys()]) close(id);
          local && local.getTracks().forEach((t) => t.stop());
          local = null;
          break;
      }
    } catch (e) {
      send({ type: "error", message: String(e && e.message || e) });
    }
  };
})();
function processGroupCommand(cmd) { weftGroup(cmd); }
