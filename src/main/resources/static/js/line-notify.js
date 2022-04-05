$(function () {
  $('.register-btn').on('click', () => {
    window.location.href = '/api/registrations/register';
  });

  const url = '/api/registrations';

  const $registrationsTable = $('#registrations tbody');
  $registrationsTable.empty();
  $.ajax(url)
  .done(registrations => {
    if (registrations.length === 0) {
      const $tr = $(document.createElement('tr'));
      $tr.append($(document.createElement('td'))
      .addClass('text-center')
      .text('No registrations found')
      .attr('colspan', 4));
      $registrationsTable.append($tr);
    }
    registrations.forEach((registration, index) => {
      const $tr = $(document.createElement('tr'));
      const seqNo = index + 1;
      $tr.append(
        $(document.createElement('th')).text(seqNo).attr('scope', 'row'));
      $tr.append($(document.createElement('td')).text(registration.targetType));
      $tr.append($(document.createElement('td')).text(registration.target));
      $tr.append(`
        <td data-type="${registration.targetType}"
          data-target="${registration.target}">
          <div>
            <button type="button" id="revoke-btn-${seqNo}"
                    class="revoke-btn btn btn-danger notify-button"
                    data-index="${seqNo}">Revoke
            </button>
            <button type="button" id="notify-btn-${seqNo}"
                    class="notify-btn btn btn-primary notify-button"
                    data-index="${seqNo}" disabled>Notify
            </button>
            <span id="notify-result-${seqNo}"
              class="notify-result invisible">Message sent...</span>
          </div>
          <div class="mt-2">
            <label>Send message</label>
            <textarea id="notify-msg-${seqNo}"
                      class="notify-msg form-control" rows="3"
                      data-index="${seqNo}"></textarea>
          </div>
        </td>`);
      $registrationsTable.append($tr);
    })

    // Toggle .notify-btn
    $('.notify-msg').on('keyup change', event => {
      const $this = $(event.target);
      const value = $.trim($this.val());
      const index = $this.data('index');
      $(`#notify-result-${index}`).addClass('invisible');
      if (value !== '') {
        $(`#notify-btn-${index}`).prop('disabled', false)
      } else {
        $(`#notify-btn-${index}`).prop('disabled', true)
      }
    });

    $('.notify-btn').on('click', event => {
      const $this = $(event.target);
      const container = $this.parents('td');
      const type = container.data('type');
      const target = container.data('target');
      const msg = $(`#notify-msg-${$this.data('index')}`).val();
      $.ajax({
        url,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({type, target, msg})
      })
      .done(() => {
        $this.next().removeClass('invisible');
      })
    });

    $('.revoke-btn').on('click', event => {
      const $this = $(event.target);
      const container = $this.parents('td');
      const type = container.data('type');
      const target = container.data('target');
      $.ajax({
        url,
        method: 'DELETE',
        headers: {
          type,
          target
        }
      })
      .done(() => {
        $this.parents('tr').addClass('deleted').end()
        .parents('td').text('Registration deleted...');
      })
    });
  })
});
