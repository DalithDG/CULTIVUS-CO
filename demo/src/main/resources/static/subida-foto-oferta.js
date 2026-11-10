/**
 * Subida de foto del vendedor con recorte cuadrado centrado 1:1,
 * redimensionado a máx. 1200x1200 y compresión en <canvas> a WebP
 * (o JPEG 0.8 si el navegador no soporta WebP), objetivo <= 200 KB.
 * Se envía como multipart (Blob) a /vendedor/imagenes/subir con CSRF.
 * Incluye controles para cancelar carga (X / botón), volver a cargar, reintentar y quitar foto.
 */
(function () {
    'use strict';

    var MAX_DIMENSION = 1200;
    var TARGET_MAX_BYTES = 200 * 1024; // 200 KB
    var HARD_MAX_BYTES = 3 * 1024 * 1024; // 3 MB
    var PLACEHOLDER_SRC = '/images/placeholders/sin-foto.svg';

    function canvasToBlob(canvas, type, quality) {
        return new Promise(function (resolve) {
            if (!canvas.toBlob) {
                resolve(null);
                return;
            }
            canvas.toBlob(function (blob) {
                resolve(blob);
            }, type, quality);
        });
    }

    function cargarImagen(file) {
        return new Promise(function (resolve, reject) {
            var url = URL.createObjectURL(file);
            var img = new Image();
            img.onload = function () {
                URL.revokeObjectURL(url);
                resolve(img);
            };
            img.onerror = function () {
                URL.revokeObjectURL(url);
                reject(new Error('No se pudo leer la imagen seleccionada. Elige un archivo JPEG, PNG o WebP válido.'));
            };
            img.src = url;
        });
    }

    function dibujarCuadrado1a1(img, maxDim) {
        var w = img.naturalWidth || img.width;
        var h = img.naturalHeight || img.height;
        if (!w || !h) {
            throw new Error('Dimensiones de imagen inválidas.');
        }
        var side = Math.min(w, h);
        var sx = Math.floor((w - side) / 2);
        var sy = Math.floor((h - side) / 2);
        var outSide = Math.min(maxDim, side);

        var canvas = document.createElement('canvas');
        canvas.width = outSide;
        canvas.height = outSide;
        var ctx = canvas.getContext('2d');
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        ctx.drawImage(img, sx, sy, side, side, 0, 0, outSide, outSide);
        return canvas;
    }

    async function comprimirImagen1a1(file) {
        var img = await cargarImagen(file);
        var dims = [MAX_DIMENSION, 960, 800];
        var calidades = [0.82, 0.72, 0.62, 0.52];
        var mejorBlob = null;

        for (var d = 0; d < dims.length; d++) {
            var canvas = dibujarCuadrado1a1(img, dims[d]);
            for (var q = 0; q < calidades.length; q++) {
                var calidad = calidades[q];
                var blob = await canvasToBlob(canvas, 'image/webp', calidad);
                // Si el navegador no soporta WebP, cae a JPEG 0.8 (o la calidad actual)
                if (!blob || blob.type !== 'image/webp') {
                    var jpegQ = q === 0 ? 0.8 : calidad;
                    blob = await canvasToBlob(canvas, 'image/jpeg', jpegQ);
                }
                if (blob) {
                    mejorBlob = blob;
                    if (blob.size <= TARGET_MAX_BYTES) {
                        return blob;
                    }
                }
            }
        }

        if (!mejorBlob) {
            throw new Error('Tu navegador no pudo procesar la imagen.');
        }
        return mejorBlob;
    }

    function obtenerCsrf() {
        var metaToken = document.querySelector('meta[name="_csrf"]');
        var metaHeader = document.querySelector('meta[name="_csrf_header"]');
        if (metaToken && metaToken.content) {
            return {
                header: (metaHeader && metaHeader.content) ? metaHeader.content : 'X-CSRF-TOKEN',
                token: metaToken.content
            };
        }
        var inputCsrf = document.querySelector('input[name="_csrf"]');
        if (inputCsrf && inputCsrf.value) {
            return { header: 'X-CSRF-TOKEN', token: inputCsrf.value };
        }
        return null;
    }

    function inicializarUploader(uploader) {
        var fileInput = uploader.querySelector('.photo-uploader__input');
        var hiddenUrl = uploader.querySelector('.photo-uploader__url');
        var previewImg = uploader.querySelector('.photo-uploader__preview');
        var statusEl = uploader.querySelector('.photo-uploader__status');
        var loadingTextEl = uploader.querySelector('.photo-uploader__loading-text');
        var clearBtn = uploader.querySelector('.photo-uploader__clear');
        var cancelUploadBtn = uploader.querySelector('.photo-uploader__cancel-upload');
        var changeBtn = uploader.querySelector('.photo-uploader__btn--change');
        var retryBtn = uploader.querySelector('.photo-uploader__btn--retry');
        var removeBtn = uploader.querySelector('.photo-uploader__btn--remove');
        var form = uploader.closest('form');
        var esObligatoria = uploader.getAttribute('data-required') === 'true';

        var subiendo = false;
        var abortController = null;
        var ultimoBlob = null;
        var localPreviewUrl = null;
        var promesaSubidaActual = null;
        var enviandoFormulario = false;

        if (!fileInput || !hiddenUrl) return;

        function liberarLocalPreview() {
            if (localPreviewUrl) {
                try {
                    URL.revokeObjectURL(localPreviewUrl);
                } catch (ignorado) {}
                localPreviewUrl = null;
            }
        }

        function setEstado(texto, tipo) {
            if (!statusEl) return;
            statusEl.textContent = texto || '';
            statusEl.classList.remove('is-error', 'is-ok');
            uploader.classList.remove('has-error', 'is-uploaded');
            if (tipo === 'error') {
                statusEl.classList.add('is-error');
                uploader.classList.add('has-error');
            }
            if (tipo === 'ok') {
                statusEl.classList.add('is-ok');
                uploader.classList.add('is-uploaded');
            }
        }

        function setLoadingTexto(texto) {
            if (loadingTextEl) {
                loadingTextEl.textContent = texto || 'Procesando y subiendo...';
            }
        }

        function limpiarOQuitarFoto(mensajePersonalizado) {
            if (abortController) {
                try {
                    abortController.abort();
                } catch (ignorado) {}
                abortController = null;
            }
            subiendo = false;
            promesaSubidaActual = null;
            ultimoBlob = null;
            fileInput.value = '';
            hiddenUrl.value = '';
            liberarLocalPreview();

            if (previewImg) {
                previewImg.src = PLACEHOLDER_SRC;
            }
            uploader.classList.remove('is-loading', 'has-photo', 'has-error', 'is-uploaded');

            var msgPorDefecto = esObligatoria
                ? 'Foto eliminada. Selecciona una foto propia de tu producto.'
                : 'Foto eliminada. Puedes subir una nueva foto o conservar la de referencia.';
            setEstado(mensajePersonalizado || msgPorDefecto, null);
        }

        async function subirBlobACloudinary(blob) {
            if (!blob) return false;

            if (abortController) {
                try {
                    abortController.abort();
                } catch (ignorado) {}
            }
            abortController = typeof AbortController !== 'undefined' ? new AbortController() : null;
            subiendo = true;
            uploader.classList.add('is-loading');
            uploader.classList.remove('has-error');
            setLoadingTexto('Subiendo foto a Cultivus...');
            setEstado('Subiendo foto a Cultivus...', null);

            var ext = blob.type === 'image/webp' ? 'webp' : 'jpg';
            var formData = new FormData();
            formData.append('archivo', blob, 'oferta-' + Date.now() + '.' + ext);

            var headers = {};
            var csrf = obtenerCsrf();
            if (csrf) {
                headers[csrf.header] = csrf.token;
                formData.append('_csrf', csrf.token);
            }

            try {
                var fetchOpts = {
                    method: 'POST',
                    headers: headers,
                    body: formData,
                    credentials: 'same-origin'
                };
                if (abortController) {
                    fetchOpts.signal = abortController.signal;
                }

                var resp = await fetch('/vendedor/imagenes/subir', fetchOpts);
                var data = null;
                try {
                    data = await resp.json();
                } catch (ignorado) {
                    data = null;
                }

                if (!resp.ok || !data || !data.url) {
                    var errorDetalle = data && (data.message || data.error);
                    if (data && data.error && data.message && data.error !== data.message && data.error !== 'Forbidden') {
                        errorDetalle = data.error + ': ' + data.message;
                    }
                    var msg = errorDetalle
                        || 'No se pudo subir la imagen al servidor (código ' + resp.status + ').';
                    throw new Error(msg);
                }

                hiddenUrl.value = data.url;
                if (previewImg) {
                    previewImg.src = data.url;
                    uploader.classList.add('has-photo');
                }
                liberarLocalPreview();
                var kb = Math.max(1, Math.round(blob.size / 1024));
                setEstado('Foto cargada y lista para publicar (' + kb + ' KB · formato cuadrado 1:1).', 'ok');
                return true;
            } catch (err) {
                if (err && err.name === 'AbortError') {
                    return false;
                }
                hiddenUrl.value = '';
                var detalleError = (err && err.message) ? err.message : 'Error al subir la imagen.';
                setEstado(detalleError + ' Puedes pulsar "Reintentar subida" o cambiar la foto.', 'error');
                return false;
            } finally {
                subiendo = false;
                abortController = null;
                uploader.classList.remove('is-loading');
            }
        }

        async function procesarArchivoSeleccionado(file) {
            if (!file) return;

            if (!file.type || !file.type.startsWith('image/')) {
                setEstado('Selecciona un archivo de imagen válido (JPEG, PNG o WebP).', 'error');
                fileInput.value = '';
                return;
            }

            subiendo = true;
            uploader.classList.add('is-loading');
            setLoadingTexto('Recortando 1:1 y optimizando...');
            setEstado('Recortando 1:1 y optimizando imagen...', null);

            try {
                var blob = await comprimirImagen1a1(file);
                if (blob.size > HARD_MAX_BYTES) {
                    throw new Error('La imagen optimizada supera el máximo permitido de 3 MB.');
                }

                ultimoBlob = blob;
                hiddenUrl.value = '';

                // Vista previa local inmediata del recorte cuadrado 1:1
                if (previewImg) {
                    liberarLocalPreview();
                    localPreviewUrl = URL.createObjectURL(blob);
                    previewImg.src = localPreviewUrl;
                    uploader.classList.add('has-photo');
                }

                promesaSubidaActual = subirBlobACloudinary(blob);
                await promesaSubidaActual;
            } catch (err) {
                subiendo = false;
                uploader.classList.remove('is-loading');
                setEstado(err && err.message ? err.message : 'Error al procesar la imagen.', 'error');
            }
        }

        // Evento al seleccionar archivo
        fileInput.addEventListener('change', function () {
            var file = fileInput.files && fileInput.files[0];
            // Limpiamos el input para permitir volver a elegir exactamente el mismo archivo si cancela
            fileInput.value = '';
            if (!file) return;
            procesarArchivoSeleccionado(file);
        });

        // Botón X en la esquina superior derecha
        if (clearBtn) {
            clearBtn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                limpiarOQuitarFoto('Carga cancelada. Toca el recuadro para elegir una foto.');
            });
        }

        // Botón "Cancelar carga" durante la subida
        if (cancelUploadBtn) {
            cancelUploadBtn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                limpiarOQuitarFoto('Subida cancelada. Puedes volver a cargar otra foto cuando quieras.');
            });
        }

        // Botón "Cambiar / Volver a cargar"
        if (changeBtn) {
            changeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                fileInput.value = '';
                fileInput.click();
            });
        }

        // Botón "Reintentar subida" (reintenta subir el recorte 1:1 ya generado sin pedir archivo otra vez)
        if (retryBtn) {
            retryBtn.addEventListener('click', function (e) {
                e.preventDefault();
                if (ultimoBlob) {
                    promesaSubidaActual = subirBlobACloudinary(ultimoBlob);
                } else {
                    fileInput.value = '';
                    fileInput.click();
                }
            });
        }

        // Botón "Quitar foto"
        if (removeBtn) {
            removeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                limpiarOQuitarFoto();
            });
        }

        // Al enviar el formulario ("Publicar Oferta" / "Guardar Cambios")
        if (form) {
            form.addEventListener('submit', async function (e) {
                if (enviandoFormulario) {
                    return;
                }

                // 1. Si ya tiene URL subida en Cloudinary, dejar enviar normalmente
                if (hiddenUrl.value && hiddenUrl.value.trim()) {
                    return;
                }

                // 2. Si hay una subida en curso o hay una foto recortada pendiente de subir,
                //    la subimos automáticamente antes de enviar el formulario.
                if (subiendo || ultimoBlob) {
                    e.preventDefault();
                    var exito = false;
                    if (subiendo && promesaSubidaActual) {
                        setEstado('Terminando de subir tu foto para publicar la oferta...', null);
                        exito = await promesaSubidaActual;
                    } else if (ultimoBlob) {
                        setEstado('Subiendo tu foto recortada antes de publicar...', null);
                        promesaSubidaActual = subirBlobACloudinary(ultimoBlob);
                        exito = await promesaSubidaActual;
                    }

                    if (exito && hiddenUrl.value && hiddenUrl.value.trim()) {
                        enviandoFormulario = true;
                        form.submit();
                    }
                    return;
                }

                // 3. No hay ni URL ni foto seleccionada, y la foto es obligatoria
                if (esObligatoria) {
                    e.preventDefault();
                    setEstado('Sube una foto propia de tu producto para publicar la oferta.', 'error');
                    fileInput.focus();
                }
            });
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        var uploaders = document.querySelectorAll('.photo-uploader');
        uploaders.forEach(inicializarUploader);
    });
})();
