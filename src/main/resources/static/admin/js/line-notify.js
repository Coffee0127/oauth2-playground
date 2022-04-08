$(function () {
  $('#timezone').text(`(${Intl.DateTimeFormat().resolvedOptions().timeZone})`);

  const formatDate = time => {
    if (!time) {
      return 'N/A';
    }

    const lpad = number => number.toString().length < 2 ? `0${number}` : number;

    const d = new Date(time);
    return `${d.getFullYear()}-${lpad(d.getMonth() + 1)}-${lpad(
      d.getDate())} ${lpad(d.getHours())}:${lpad(d.getMinutes())}`
  }

  const cleanup = element =>
    element.parents('tr').addClass('deleted').end()
    .parents('td').text('Registration deleted...');

  const url = '/admin/api/registrations';

  const $registrationsTable = $('#registrations tbody');
  $registrationsTable.empty();
  $.ajax(url)
  .done(registrations => {
    if (registrations.length === 0) {
      const $tr = $(document.createElement('tr'));
      $tr.append($(document.createElement('td'))
      .addClass('text-center')
      .text('No registrations found')
      .attr('colspan', 6));
      $registrationsTable.append($tr);
    }

    const now = new Date().getTime();
    registrations.forEach((registration, index) => {
      const seqNo = index + 1;
      const $tr = $(document.createElement('tr')).addClass('registration')
      .data('userId', registration.userId)
      .data('type', registration.targetType)
      .data('target', registration.target);
      $tr.append($(document.createElement('td')).append(
        $('<input/>', {type: 'checkbox'})
      ));
      $tr.append(
        $(document.createElement('th')).text(seqNo).attr('scope', 'row'));
      $tr.append($(document.createElement('td')).text(registration.userName));
      $tr.append($(document.createElement('td')).text(registration.targetType));
      $tr.append($(document.createElement('td')).text(registration.target));
      $tr.append($(document.createElement('td')).text(
        formatDate(registration.expiryTime)));
      // Register cleanup
      setTimeout(() => {
        cleanup($(`#registration-${seqNo}`));
      }, registration.expiryTime - now);

      $tr.on('click', event => {
        const checkbox = $(event.target).parent('tr').find('input');
        checkbox.prop('checked', !checkbox.is(':checked'));
      })

      $registrationsTable.append($tr);
    })

    // Toggle .notify-btn
    const $notifyResult = $('#notify-result');
    $('#notify-msg').on('keyup change', event => {
      const $this = $(event.target);
      const value = $.trim($this.val());
      $notifyResult.addClass('invisible');
      if (value !== '') {
        $('#notify-btn').prop('disabled', false);
      } else {
        $('#notify-btn').prop('disabled', true);
      }
    });

    $('#notify-btn').on('click', () => {
      const msg = $('#notify-msg').val();
      const elements = [];
      $('.registration input:checked').each((index, element) => {
        elements.push($(element).parents('tr'));
      });
      if (elements.length === 0) {
        alert('Please select target to send notification.');
        return;
      }
      const payload = elements.map(element => {
        const userId = element.data('userId');
        const type = element.data('type');
        const target = element.data('target');
        return {userId, type, target, msg};
      });
      $.ajax({
        url,
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(payload)
      })
      .done(() => {
        $notifyResult.removeClass('invisible');
      });
    });
  })
});
